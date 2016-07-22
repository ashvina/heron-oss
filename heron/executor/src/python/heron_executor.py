# Copyright 2016 Twitter. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/usr/bin/env python2.7

''' heron-executor '''
import atexit
import datetime
import os
import sys
import signal
import subprocess
import time
import json
import base64
import string
import random
import yaml

from functools import partial

# pylint: disable=unused-import
from heron.proto.packing_plan_pb2 import PackingPlan
from heron.statemgrs.src.python import statemanagerfactory
from heron.statemgrs.src.python.config import Config as StateMgrConfig

STATEMGRS_KEY = "statemgrs"

def print_usage():
  print (
      "./heron-executor <shardid> <topname> <topid> <topdefnfile> "
      " <instance_distribution> <zknode> <zkroot> <tmaster_binary> <stmgr_binary> "
      " <metricsmgr_classpath> <instance_jvm_opts_in_base64> <classpath> "
      " <master_port> <tmaster_controller_port> <tmaster_stats_port> <heron_internals_config_file> "
      " <component_rammap> <component_jvm_opts_in_base64> <pkg_type> <topology_jar_file>"
      " <heron_java_home> <shell-port> <heron_shell_binary> <metricsmgr_port>"
      " <cluster> <role> <environ> <instance_classpath> <metrics_sinks_config_file> "
      " <scheduler_classpath> <scheduler_port>")

def do_print(statement):
  timestr = datetime.datetime.fromtimestamp(time.time()).strftime('%Y-%m-%d %H:%M:%S')
  print "%s: %s" % (timestr, statement)
  sys.stdout.flush()

def extract_triplets(s):
  """
  given a string s of the form "a:b:c:d:e:f...", extract triplets
  [('a', 'b', 'c'), ('d', 'e', 'f')]
  """
  t = s.split(':')
  assert len(t) % 3 == 0
  result = []
  while len(t) > 0:
    result.append((t[0], t[1], t[2]))
    t = t[3:]
  return result

def id_list(prefix, start, count):
  ids = []
  for i in range(start, count + 1):
    ids.append(prefix + str(i))
  return ids

def stmgr_list(count):
  return id_list("stmgr-", 1, count)

def metricsmgr_list(count):
  return id_list("metricsmgr-", 0, count)

def heron_shell_list(count):
  return id_list("heron-shell-", 0, count)

def get_heron_executor_process_name(shard_id):
  return 'heron-executor-' + str(shard_id)

def get_process_pid_filename(process_name):
  return '%s.pid' % process_name

def get_tmp_filename():
  return '%s.heron.tmp' % (''.join(random.choice(string.ascii_uppercase) for i in range(12)))

def atomic_write_file(path, content):
  """
  file.write(...) is not atomic.
  We write to a tmp file and then rename to target path since rename is atomic.
  We do this to avoid the content of file is dirty read/partially read by others.
  """
  # Write to a randomly tmp file
  tmp_file = get_tmp_filename()
  with open(tmp_file, 'w') as f:
    f.write(content)
    # make sure that all data is on disk
    f.flush()
    os.fsync(f.fileno())

  # Rename the tmp file
  os.rename(tmp_file, path)

def log_pid_for_process(process_name, pid):
  filename = get_process_pid_filename(process_name)
  do_print('Logging pid %d to file %s' %(pid, filename))
  atomic_write_file(filename, str(pid))

# pylint: disable=too-many-instance-attributes
class HeronExecutor(object):
  """ Heron executor is a class that is responsible for running each of the process on a given
  container. Based on the container id and the instance distribution, it determines if the container
  is a master node or a worker node and it starts processes accordingly."""
  def __init__(self, args):
    self.max_runs = 100
    self.interval_between_runs = 10
    self.shard = int(args[1])
    self.topology_name = args[2]
    self.topology_id = args[3]
    self.topology_defn_file = args[4]
    self.zknode = args[6]
    self.zkroot = args[7]
    self.tmaster_binary = args[8]
    self.stmgr_binary = args[9]
    self.metricsmgr_classpath = args[10]
    self.instance_jvm_opts =\
        base64.b64decode(args[11].lstrip('"').rstrip('"').replace('&equals;', '='))
    self.classpath = args[12]
    self.master_port = args[13]
    self.tmaster_controller_port = args[14]
    self.tmaster_stats_port = args[15]
    self.heron_internals_config_file = args[16]
    self.component_rammap =\
        map(lambda x: {x.split(':')[0]: int(x.split(':')[1])}, args[17].split(','))
    self.component_rammap =\
        reduce(lambda x, y: dict(x.items() + y.items()), self.component_rammap)

    # component_jvm_opts_in_base64 itself is a base64-encoding-json-map, which is appended with
    # " at the start and end. It also escapes "=" to "&equals" due to aurora limitation
    # And the json is a map from base64-encoding-component-name to base64-encoding-jvm-options
    self.component_jvm_opts = {}
    # First we need to decode the base64 string back to a json map string
    component_jvm_opts_in_json =\
        base64.b64decode(args[18].lstrip('"').rstrip('"').replace('&equals;', '='))
    if component_jvm_opts_in_json != "":
      for (k, v) in json.loads(component_jvm_opts_in_json).items():
        # In json, the component name and jvm options are still in base64 encoding
        self.component_jvm_opts[base64.b64decode(k)] = base64.b64decode(v)

    self.pkg_type = args[19]
    self.topology_jar_file = args[20]
    self.heron_java_home = args[21]
    self.shell_port = args[22]
    self.heron_shell_binary = args[23]
    self.metricsmgr_port = args[24]
    self.cluster = args[25]
    self.role = args[26]
    self.environ = args[27]
    self.instance_classpath = args[28]
    self.metrics_sinks_config_file = args[29]
    self.scheduler_classpath = args[30]
    self.scheduler_port = args[31]

    # Read the heron_internals.yaml for logging dir
    self.log_dir = self.load_logging_dir(self.heron_internals_config_file)

    # these get set when we call update_instance_distribution
    self.instance_distribution = {}
    self.stmgr_ids = []
    self.metricsmgr_ids = []
    self.heron_shell_ids = []

    # This gets set once processes are launched
    # pylint: disable=fixme
    # TODO: we need to synchronize rw to this dict since is used by multiple threads
    self.processes_to_monitor = {}

    # Log itself pid
    log_pid_for_process(get_heron_executor_process_name(self.shard), os.getpid())

  # pylint: disable=no-self-use
  def load_logging_dir(self, heron_internals_config_file):
    with open(heron_internals_config_file, 'r') as stream:
      heron_internals_config = yaml.load(stream)
    return heron_internals_config['heron.logging.directory']

  def update_instance_distribution(self, new_distribution):
    self.instance_distribution = new_distribution
    self.stmgr_ids = stmgr_list(len(self.instance_distribution))
    self.metricsmgr_ids = metricsmgr_list(len(self.instance_distribution))
    self.heron_shell_ids = heron_shell_list(len(self.instance_distribution))

  # pylint: disable=no-self-use
  def parse_instance_distribution(self, encoded_distribution):
    # First get the container id to distribution encoding
    f1 = map(lambda z: (int(z[0]), ':'.join(z[1:])),
             map(lambda x: x.split(':'), encoded_distribution.split(',')))
    # Next get the triplets
    f2 = map(lambda x: {x[0] : extract_triplets(x[1])}, f1)
    # Finally convert that into a map
    return reduce(lambda x, y: dict(x.items() + y.items()), f2)

  def get_metricsmgr_cmd(self, metricsManagerId, sink_config_file, port):
    ''' get the command to start the metrics manager processes '''
    metricsmgr_main_class = 'com.twitter.heron.metricsmgr.MetricsManager'

    metricsmgr_cmd = [os.path.join(self.heron_java_home, 'bin/java'),
                      # We could not rely on the default -Xmx setting, which could be very big,
                      # for instance, the default -Xmx in Twitter mesos machine is around 18GB
                      '-Xmx1024M',
                      '-XX:+PrintCommandLineFlags',
                      '-verbosegc',
                      '-XX:+PrintGCDetails',
                      '-XX:+PrintGCTimeStamps',
                      '-XX:+PrintGCDateStamps',
                      '-XX:+PrintGCCause',
                      '-XX:+PrintPromotionFailure',
                      '-XX:+PrintTenuringDistribution',
                      '-XX:+PrintHeapAtGC',
                      '-XX:+HeapDumpOnOutOfMemoryError',
                      '-XX:+UseConcMarkSweepGC',
                      '-XX:+PrintCommandLineFlags',
                      '-Xloggc:log-files/gc.metricsmgr.log',
                      '-Djava.net.preferIPv4Stack=true',
                      '-cp',
                      self.metricsmgr_classpath,
                      metricsmgr_main_class,
                      metricsManagerId,
                      port,
                      self.topology_name,
                      self.topology_id,
                      self.heron_internals_config_file,
                      sink_config_file]

    return metricsmgr_cmd

  def get_tmaster_processes(self):
    ''' get the command to start the tmaster processes '''
    retval = {}
    tmaster_cmd = [
        self.tmaster_binary,
        self.master_port,
        self.tmaster_controller_port,
        self.tmaster_stats_port,
        self.topology_name,
        self.topology_id,
        self.zknode,
        self.zkroot,
        ','.join(self.stmgr_ids),
        self.heron_internals_config_file,
        self.metrics_sinks_config_file,
        self.metricsmgr_port]
    retval["heron-tmaster"] = tmaster_cmd

    # metricsmgr_metrics_sink_config_file = 'metrics_sinks.yaml'

    retval[self.metricsmgr_ids[0]] = self.get_metricsmgr_cmd(
        self.metricsmgr_ids[0],
        self.metrics_sinks_config_file,
        self.metricsmgr_port)

    return retval

  def get_scheduler_processes(self):
    retval = {}
    scheduler_cmd = [
        'java',
        '-cp',
        self.scheduler_classpath,
        'com.twitter.heron.scheduler.SchedulerMain',
        self.cluster,
        self.role,
        self.environ,
        self.topology_name,
        self.topology_jar_file,
        self.scheduler_port]
    retval["heron-tscheduler"] = scheduler_cmd

    return retval


  def get_streaming_processes(self):
    '''
    Returns the processes to handle streams, including the stream-mgr and the user code containing
    the stream logic of the topology
    '''
    retval = {}
    # First lets make sure that our shard id is a valid one
    assert self.shard in self.instance_distribution
    my_instances = self.instance_distribution[self.shard]
    instance_info = []
    for (component_name, global_task_id, component_index) in my_instances:
      instance_id = "container_" + str(self.shard) + "_" + \
        component_name + "_" + str(global_task_id)
      instance_info.append((instance_id, component_name, global_task_id, component_index))

    stmgr_cmd = [
        self.stmgr_binary,
        self.topology_name,
        self.topology_id,
        self.topology_defn_file,
        self.zknode,
        self.zkroot,
        self.stmgr_ids[self.shard - 1],
        ','.join(map(lambda x: x[0], instance_info)),
        self.master_port,
        self.metricsmgr_port,
        self.shell_port,
        self.heron_internals_config_file]
    retval[self.stmgr_ids[self.shard - 1]] = stmgr_cmd

    # metricsmgr_metrics_sink_config_file = 'metrics_sinks.yaml'

    retval[self.metricsmgr_ids[self.shard]] = self.get_metricsmgr_cmd(
        self.metricsmgr_ids[self.shard],
        self.metrics_sinks_config_file,
        self.metricsmgr_port
    )

    # TO DO (Karthik) to be moved into keys and defaults files
    code_cache_size_mb = 64
    perm_gen_size_mb = 128

    for (instance_id, component_name, global_task_id, component_index) in instance_info:
      total_jvm_size = int(self.component_rammap[component_name] / (1024 * 1024))
      heap_size_mb = total_jvm_size - code_cache_size_mb - perm_gen_size_mb
      # pylint: disable=line-too-long
      do_print("component name: %s, ram request: %d, total jvm size: %dM, cache size: %dM, perm size: %dM"
               % (component_name, self.component_rammap[component_name],
                  total_jvm_size, code_cache_size_mb, perm_gen_size_mb))
      xmn_size = int(heap_size_mb / 2)
      instance_cmd = [os.path.join(self.heron_java_home, 'bin/java'),
                      '-Xmx%dM' % heap_size_mb,
                      '-Xms%dM' % heap_size_mb,
                      '-Xmn%dM' % xmn_size,
                      '-XX:MaxPermSize=%dM' % perm_gen_size_mb,
                      '-XX:PermSize=%dM' % perm_gen_size_mb,
                      '-XX:ReservedCodeCacheSize=%dM' % code_cache_size_mb,
                      '-XX:+CMSScavengeBeforeRemark',
                      '-XX:TargetSurvivorRatio=90',
                      '-XX:+PrintCommandLineFlags',
                      '-verbosegc',
                      '-XX:+PrintGCDetails',
                      '-XX:+PrintGCTimeStamps',
                      '-XX:+PrintGCDateStamps',
                      '-XX:+PrintGCCause',
                      '-XX:+PrintPromotionFailure',
                      '-XX:+PrintTenuringDistribution',
                      '-XX:+PrintHeapAtGC',
                      '-XX:+HeapDumpOnOutOfMemoryError',
                      '-XX:+UseConcMarkSweepGC',
                      '-XX:ParallelGCThreads=4',
                      '-Xloggc:log-files/gc.%s.log' % instance_id]
      instance_cmd = instance_cmd + self.instance_jvm_opts.split()
      if component_name in self.component_jvm_opts:
        instance_cmd = instance_cmd + self.component_jvm_opts[component_name].split()
      instance_cmd.extend(['-Djava.net.preferIPv4Stack=true',
                           '-cp',
                           '%s:%s' % (self.instance_classpath, self.classpath),
                           'com.twitter.heron.instance.HeronInstance',
                           self.topology_name,
                           self.topology_id,
                           instance_id,
                           component_name,
                           global_task_id,
                           component_index,
                           self.stmgr_ids[self.shard - 1],
                           self.master_port,
                           self.metricsmgr_port,
                           self.heron_internals_config_file])
      retval[instance_id] = instance_cmd
    return retval

  # Returns the common heron support processes that all containers get, like the heron shell
  def get_heron_support_processes(self):
    """ Get a map from all daemon services' name to the command to start them """
    retval = {}

    retval[self.heron_shell_ids[self.shard]] = [
        '%s' % self.heron_shell_binary,
        '--port=%s' % self.shell_port,
        '--log_file_prefix=%s/heron-shell.log' % self.log_dir]

    return retval

  def untar_if_tar(self):
    if self.pkg_type == "tar":
      os.system("tar -xvf %s" % self.topology_jar_file)

  # pylint: disable=no-self-use
  def wait_process_std_out_err(self, name, process):
    ''' Wait for the termination of a process and log its stdout & stderr '''
    (process_stdout, process_stderr) = process.communicate()
    if process_stdout:
      do_print("%s stdout: %s" %(name, process_stdout))
    if process_stderr:
      do_print("%s stderr: %s" %(name, process_stderr))

  def run_process(self, name, cmd):
    do_print("Running %s process as %s" % (name, ' '.join(cmd)))
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

  def run_blocking_process(self, cmd, is_shell):
    do_print("Running blocking process as %s" % cmd)
    process = subprocess.Popen(cmd, shell=is_shell, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    # wait for termination
    self.wait_process_std_out_err("", process)

    # return the exit code
    return process.returncode

  def kill_processes(self, commands):
    # remove the command from processes_to_monitor and kill the process
    for command_name, command in commands.iteritems():
      for p, name, _, _ in self.processes_to_monitor.values():
        if name == command_name:
          del self.processes_to_monitor[p.pid]
          do_print("Killing %s process with pid %s: %s" % (name, p.pid, ' '.join(command)))
          p.kill()

  def start_processes(self, commands):
    """Start all commands and add them to the dict of processes to be monitored """
    processes_to_monitor = {}
    # First start all the processes
    for (name, command) in commands.items():
      p = self.run_process(name, command)
      processes_to_monitor[p.pid] = (p, name, command, 1)

      # Log down the pid file
      log_pid_for_process(name, p.pid)

    self.processes_to_monitor.update(processes_to_monitor)

  def monitor_processes(self):
    """ Monitor all processes in processes_to_monitor dict,
    restarting any if they fail, up to max_runs times.
    """
    # Now wait for any child to die
    while True:
      if len(self.processes_to_monitor) > 0:
        (pid, status) = os.wait()
        if pid in self.processes_to_monitor.keys():
          (old_p, name, command, nattempts) = self.processes_to_monitor[pid]
          do_print("%s (pid=%s) exited with status %d. command=%s" % (name, pid, status, command))
          # Log the stdout & stderr of the failed process
          self.wait_process_std_out_err(name, old_p)

          # Just make it world readable
          if os.path.isfile("core.%d" % pid):
            os.system("chmod a+r core.%d" % pid)
          if nattempts > self.max_runs:
            do_print("%s exited too many times" % name)
            sys.exit(1)
          time.sleep(self.interval_between_runs)
          p = self.run_process(name, command)
          del self.processes_to_monitor[pid]
          self.processes_to_monitor[p.pid] = (p, name, command, nattempts + 1)

          # Log down the pid file
          log_pid_for_process(name, p.pid)

  def get_commands_to_run(self):
    if self.shard == 0:
      commands = self.get_tmaster_processes()
    else:
      self.untar_if_tar()
      commands = self.get_streaming_processes()

    # Attach daemon processes
    commands.update(self.get_heron_support_processes())
    return commands

  def get_command_changes(self, current_commands, updated_commands):
    """
    Compares the current command with updated command to return a 3-tuple of dicts,
    keyed by command name: commands_to_kill, commands_to_keep and commands_to_start.
    """
    commands_to_kill = {}
    commands_to_keep = {}
    commands_to_start = {}

    # if the current command has a matching command in the updated commands we keep it
    # otherwise we kill it
    for current_name, current_command in current_commands.iteritems():
      if current_name in updated_commands.keys() and \
        current_command == updated_commands[current_name] and \
        current_name != 'heron-tmaster': # always restart tmaster of instance dist has changed
        commands_to_keep[current_name] = current_command
      else:
        commands_to_kill[current_name] = current_command

    # updated commands not in the keep list need to be started
    for updated_name, updated_command in updated_commands.iteritems():
      if updated_name not in commands_to_keep.keys():
        commands_to_start[updated_name] = updated_command

    return commands_to_kill, commands_to_keep, commands_to_start

  def launch(self):
    ''' launch '''
    current_commands = dict(map((lambda x: (x[1], x[2])), self.processes_to_monitor.values()))
    updated_commands = self.get_commands_to_run()

    # get the commands to kill, keep and start
    commands_to_kill, commands_to_keep, commands_to_start = \
        self.get_command_changes(current_commands, updated_commands)

    do_print("current commands: %s" % sorted(current_commands.keys()))
    do_print("new commands    : %s" % sorted(updated_commands.keys()))
    do_print("commands_to_kill: %s" % sorted(commands_to_kill.keys()))
    do_print("commands_to_keep: %s" % sorted(commands_to_keep.keys()))
    do_print("commands_to_start: %s" % sorted(commands_to_start.keys()))

    self.kill_processes(commands_to_kill)
    self.start_processes(commands_to_start)
    do_print("Launch complete - processes killed=%s kept=%s started=%s monitored=%s" %
             (len(commands_to_kill), len(commands_to_keep),
              len(commands_to_start), len(self.processes_to_monitor)))

  def prepare_launch(self):
    create_folders = 'mkdir -p %s' % self.log_dir
    chmod_binaries = \
        'chmod a+rx . && chmod a+x %s && chmod +x %s && chmod +x %s && chmod +x %s' \
        % (self.log_dir, self.tmaster_binary, self.stmgr_binary, self.heron_shell_binary)

    commands = [create_folders, chmod_binaries]

    for command in commands:
      if self.run_blocking_process(command, True) != 0:
        do_print("Failed to run command: %s. Exiting" % command)
        sys.exit(1)

  def register_packing_plan_watcher(self, executor):
    """
    Receive updates to the packing plan from the statemgrs and update processes as needed.
    """
    # pylint: disable=fixme
    # TODO: replace with proper config loading. only for prototyping!
    statemgr_locations = [
        {
            'type': 'file',
            'name': 'local',
            'rootpath': '~/.herondata/repository/state/local',
            'tunnelhost': 'localhost',
        },
        # {
        #     'type': 'zookeeper',
        #     'name': 'zk',
        #     'hostport': 'szookeeper.local.twitter.com:2181',
        #     'rootpath': '/storm/heron/states',
        #     'tunnelhost': 'localhost',
        # },
    ]

    statemgr_config = StateMgrConfig()
    statemgr_config.set_state_locations(statemgr_locations)
    state_managers = statemanagerfactory.get_all_state_managers(statemgr_config)

    # pylint: disable=unused-argument
    def on_packing_plan_watch(state_manager, packing_plan):
      do_print(
          "State watch triggered for packing plan change. New PackingPlan: %s" % str(packing_plan))

      current_distribution = self.instance_distribution
      new_distribution = self.parse_instance_distribution(packing_plan.instance_distribution)

      if current_distribution != new_distribution:
        do_print("Instance distribution change detected on shard %s, relaunching. "\
                 "Existing: %s, new: %s" % (self.shard, current_distribution, new_distribution))
        self.update_instance_distribution(new_distribution)

        # pylint: disable=fixme
        # TODO: handle relaunch of running topology scenario
        do_print("Relaunching shard %s" % self.shard)
        executor.launch()
      else:
        do_print("Instance distribution not changed, not relaunching. existing: %s, new: %s" %
                 (current_distribution, new_distribution))

    for state_manager in state_managers:
      # The callback function with the bound
      # state_manager as first variable.
      onPackingPlanWatch = partial(on_packing_plan_watch, state_manager)
      state_manager.get_packing_plan(self.topology_name, onPackingPlanWatch)
      do_print("Registered state watch for packing plan changes with state manager %s." %
               str(state_manager))

def main():
  if len(sys.argv) != 32:
    print_usage()
    sys.exit(1)
  # pylint: disable=undefined-variable
  executor = HeronExecutor(sys.argv)
  executor.prepare_launch()
  executor.register_packing_plan_watcher(executor)
  executor.monitor_processes()

# pylint: disable=unused-argument
def signal_handler(signal_to_handle, frame):
  # We would do nothing here but just exit
  # Just catch the SIGTERM and then cleanup(), registered with atexit, would invoke
  sys.exit(signal_to_handle)

def setup():
  # Redirect stdout and stderr to files in append mode
  # The filename format is heron-executor.stdxxx
  sys.stdout = open('heron-executor.stdout', 'a')
  sys.stderr = open('heron-executor.stderr', 'a')

  do_print('Set up process group; executor becomes leader')
  os.setpgrp() # create new process group, become its leader

  do_print('Register the SIGTERM signal handler')
  signal.signal(signal.SIGTERM, signal_handler)

  do_print('Register the atexit clean up')
  atexit.register(cleanup)

def cleanup():
  """Handler to trigger when receiving the SIGTERM signal
  Do cleanup inside this method, including:
  1. Terminate all children processes
  """
  do_print('Executor terminated; exiting all process in executor.')
  # We would not wait or check whether process spawned dead or not
  os.killpg(0, signal.SIGTERM)

if __name__ == "__main__":
  setup()
  main()
