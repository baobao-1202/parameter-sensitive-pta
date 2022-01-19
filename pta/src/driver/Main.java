package driver;

import pta.ContextSensPTA;
import pta.PTA;
import pta.EagleObjectSensPTA;
import pta.ParamObjectSensPTA;

public class Main {
	public static void main(String[] args){
		Config.v().init(args);
		
		if (PTAOptions.dumpJimple) {
			String jimplePath = PTAOptions.APP_PATH.replace(".jar", "");
			SootUtils.dumpJimple(jimplePath);
			System.out.println("Jimple files have been dumped to: " + jimplePath);
			return;
		}
		
		PTA pta = PTAOptions.ptaPattern.isInsensitive()?new PTA():
			PTAOptions.ptaPattern.isEagle()?new EagleObjectSensPTA(PTAOptions.ptaPattern.getContextDepth()):
			PTAOptions.ptaPattern.isParam()?new ParamObjectSensPTA(PTAOptions.ptaPattern.getContextDepth()):
			new ContextSensPTA(PTAOptions.ptaPattern.getContextDepth(), PTAOptions.ptaPattern.getHeapContextDepth());

		pta.run();

		System.out.println(pta.evaluator());
	}
}
