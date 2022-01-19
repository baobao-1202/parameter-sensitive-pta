 #!/usr/bin/python
import os, sys, time, shutil
from collections import OrderedDict
from pta.bin.pautils import *

TIMEOUT = 86400
PTAPATH = 'pta'
BENCHMARKPATH = 'benchmarks'
DACAPODIR = 'dacapo'
APPDIR = 'applications'
OUTPUTPATH = 'output'
PRINT = False
EMPTYCONTEXT = False
OPTIONS = (
	'-timeout='+str(TIMEOUT),
	)
ANALYSES = (
	'insens',
	'1o',
	'e-1o',
	'2o',
	'e-2o',
	'3o',
	'e-3o',
	'1t',
	'e-1t',
	'2t',
	'e-2t',
	'3t',
	'e-3t',
        'p-1p',
        '1p'
	)
BENCHMARKS = (
	'antlr',
	'bloat',
	'chart',
	'eclipse',
	'fop',
	'luindex',
	'lusearch',
	'pmd',
	'xalan',
	
	'checkstyle',
	'findbugs',
	'JPC',
	)
APPJARNAMES = {
	'checkstyle':'checkstyle-5.7-all',
	'findbugs':'findbugs',
	'JPC':'JPCApplication',
}
DACAPO = (
	'antlr',
	'bloat',
	'chart',
	'eclipse',
	'fop',
	'jython',
	'luindex',
	'lusearch',
	'pmd',
	'xalan',
	)

#StaticContext = empty
UNSCALABLE_EMPTY = {
	'2o':('eclipse',),
	'3o':('antlr','bloat','chart','eclipse','pmd','xalan','checkstyle','findbugs','JPC'),
	'e-3o':('eclipse',),
}

#StaticContext = this
UNSCALABLE = {
	'2o':('eclipse'),
	'3o':('bloat','chart','eclipse','xalan','checkstyle','findbugs'), #JPC
	'e-3o':('chart','eclipse','checkstyle'),
	'3t':('eclipse'),
}

OPTIONMESSAGE = 'The valid OPTIONs are:\n'\
	+ option('-help|-h', 'print this message.')\
	+ option('-print', 'print the analyses results on screen.')\
	+ option('-clean', 'remove previous outputs.')\
	+ option('-emptycontext', 'use empty context for static calls, or virtualised context (in "Precision-Preserving Acceleration of Object-Sensitive Pointer Analysis with CFL-Reachability") as default')\
	+ option('<PTA>', 'specify pointer analysis.')\
	+ option('<Benchmark>', 'specify benchmark.')\
	+ option('-all', 'run all analyses for specified benchmark(s) if ONLY benchmark(s) is specified;\n\
				run specified analyses for all benchmark(s) if ONLY analyses is specified;\n\
				run all analyses for all benchmarks if nothing specified.')\

def getPTACommand(analysis, bm):
	cmd = sys.executable+' pta.py ' + ' '.join(OPTIONS) + ' -pta=' + analysis
	if EMPTYCONTEXT:
		cmd += ' -sctx=empty'
	if bm in DACAPO:
		cmd += ' -apppath ' + os.path.join(BENCHMARKPATH, DACAPODIR, bm + '.jar') \
			+ ' -mainclass dacapo.%s.Main'%bm \
			+ ' -libpath ' + os.path.join(BENCHMARKPATH, DACAPODIR, bm + '-deps.jar') \
			+ ' -reflectionlog '+ os.path.join(BENCHMARKPATH, DACAPODIR, bm + '-refl.log')
	else:
		cmd += ' -apppath ' + os.path.join(BENCHMARKPATH, APPDIR, bm, APPJARNAMES[bm] + '.jar') \
			+ ' -libpath ' + os.path.join(BENCHMARKPATH, APPDIR, bm) \
			+ ' -reflectionlog '+ os.path.join(BENCHMARKPATH, APPDIR, bm, bm +'-refl.log')
	return cmd

def runPTA(analysis, bm):
	outputFile = os.path.join(OUTPUTPATH, bm + '_'+analysis + '.o')
	
	cmd = getPTACommand(analysis, bm)
	if not PRINT:
		if os.path.exists(outputFile):
#			print('old result found. skip this.')
			return
		cmd += ' > ' + outputFile
	
	if analysis in UNSCALABLE and bm in UNSCALABLE[analysis]:
		print('predicted unscalable. skip this.')
		if not os.path.exists(outputFile):
			with open(outputFile, 'a') as f:
				f.write('predicted unscalable.')
		return
	
	print('now running ' +  Color.CYAN + analysis + Color.RESET +' for ' + Color.YELLOW + bm + Color.RESET + ' ...')
	os.system(cmd)

if __name__ == '__main__':
	if '-help' in sys.argv or '-h' in sys.argv:
		sys.exit(OPTIONMESSAGE)
	if '-clean' in sys.argv:
		if os.path.exists(OUTPUTPATH):
			shutil.rmtree(OUTPUTPATH)
		sys.exit()
	if "-print" in sys.argv:
		PRINT = True
	if "-emptycontext" in sys.argv:
		EMPTYCONTEXT = True
		UNSCALABLE = UNSCALABLE_EMPTY
	
	analyses=[]
	benchmarks=[]
	for arg in sys.argv:
		if arg in ANALYSES:
			analyses.append(arg)
		elif arg in BENCHMARKS:
			benchmarks.append(arg)
		elif arg.startswith('-outputpath='):
			OUTPUTPATH = arg[len('-outputpath='):]
	
	if "-all" in sys.argv:
		if len(benchmarks)==0:
			benchmarks = BENCHMARKS
		if len(analyses)==0:
			analyses = ANALYSES
	
	if len(benchmarks)==0:
		sys.exit("benchmark(s) not specified."+ OPTIONMESSAGE)
	if len(analyses)==0:
		analyses.append("insens")
	
	BENCHMARKPATH = os.path.realpath(BENCHMARKPATH)#the path containing this script
	OUTPUTPATH = os.path.realpath(OUTPUTPATH)
	os.chdir(PTAPATH)
	try:
		if not os.path.isdir(OUTPUTPATH):
			if os.path.exists(OUTPUTPATH):
				raise IOError('NC OUTPUTPATH')
			else:
				os.makedirs(OUTPUTPATH)
	except 'NC OUTPUTPATH':
		print(Color.RED + 'ERROR: ' + Color.RESET + 'CANNOT CREATE OUTPUTDIR: ' + Color.YELLOW + OUTPUTPATH + Color.RESET + ' ALREADY EXISTS AS A FILE!')
	
	for analysis in analyses:
		for bm in benchmarks:
			runPTA(analysis, bm)
