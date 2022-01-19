#!/usr/bin/python

import os
#Color
class Color:
	RESET  = '\033[0m'
	BOLD   = '\033[1m'
	GREEN  = '\033[32m'
	YELLOW = '\033[33m'
	CYAN   = '\033[36m'
	WHITE  = '\033[37m'
	RED    = '\033[91m'
	@staticmethod
	def changeToNT():
		Color.RESET  = ''
		Color.BOLD   = ''
		Color.GREEN  = ''
		Color.YELLOW = ''
		Color.CYAN   = ''
		Color.WHITE  = ''
		Color.RED    = ''
#OS
if(os.name == 'nt'):
	Color.changeToNT()
	cmdsep='&&'
	rm='del'
else:
	cmdsep='\n'
	rm='rm'

#OPTIONS
def makeup(str):
	return ' '*(30-len(str))
def bioption(opt, arg, des):
	return Color.BOLD + Color.YELLOW + opt +' '+ Color.GREEN + arg + Color.WHITE + makeup(opt + arg) + des + Color.RESET +'\n'
def option(opt, des):
	return bioption(opt, '', des)