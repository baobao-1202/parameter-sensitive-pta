#!/usr/bin/python

import os, sys, subprocess, time
from bin.pautils import *

#HOME
PA_HOME = os.path.dirname(os.path.realpath(__file__))
LIB = os.path.join(PA_HOME, 'lib')
#heapsize
XMX = '-Xmx256g'
timeout=-1

#RUNJA
CLASSPATH = os.pathsep.join([
	os.path.join(PA_HOME, 'config-files'),
	os.path.join(PA_HOME, 'classes'),
	os.path.join(LIB, 'commons-cli-1.2.jar'),
	os.path.join(LIB, 'commons-io-2.4.jar'),
	os.path.join(LIB, 'guava-23.0.jar'),
	os.path.join(LIB, 'soot-trunk.jar'),
	os.path.join(LIB, 'automaton.jar'),
	os.path.join(LIB, 'gson-2.7.jar'),
	])
runJava_cmd = 'java -Xms1g %s -cp ' + CLASSPATH + ' driver.Main %s'
OPTIONMESSAGE = 'The valid OPTIONs are:\n'\
	+ option('-help|-h', 'print this message.')\
	+ bioption('-Xmx', '\b<MAX>', '  Specify the maximum size, in bytes, of the memory allocation pool.')\
	+ bioption('-timeout', 'seconds', 'Timeout for PTA (default value: -1 (unlimited)).')\
	+ option('-ptahelp', 'print help info for pointer analysis.')\
	
	
if '-help' in sys.argv or '-h' in sys.argv:
	sys.exit(OPTIONMESSAGE)
if '-ptahelp' in sys.argv or '-ptah' in sys.argv:
	os.system(runJava_cmd %('', '-help'))
	sys.exit()
if(len(sys.argv) < 3):
	sys.exit('Not enough options! '+OPTIONMESSAGE)

i = 1
while ( i < len(sys.argv) ):
	if sys.argv[i].startswith('-timeout'):
		if sys.argv[i].startswith('-timeout='):
			timeout = sys.argv[i][9:]
			sys.argv.remove(sys.argv[i])
			i-=1
		else:
			timeout = sys.argv[i+1]
			sys.argv.remove('-timeout')
			sys.argv.remove(timeout)
			i-=2
		timeout = int(timeout)
	elif sys.argv[i].startswith('-Xmx'):
		XMX = sys.argv[i]
		sys.argv.remove(XMX)
		i-=1
	i += 1

	
#prepare javacmd args
if not ('-jre' in sys.argv):
	sys.argv.append('-jre='+os.path.join(PA_HOME, 'lib', 'jre', 'jre1.6.0_45'))

if __name__ == "__main__":
	p = subprocess.Popen((runJava_cmd %(XMX, ' '.join(sys.argv[1:]))).split(' '))
	while p.poll() is None:
		if timeout==0:
			p.kill()  
			print ('Time is out!')
			break
		else:
			time.sleep(1)
			timeout-=1;
			