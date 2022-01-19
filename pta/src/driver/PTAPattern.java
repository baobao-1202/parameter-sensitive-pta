package driver;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PTAPattern {
	enum ContextKind{INSENS,CALLSITE,OBJECT,TYPE,PARAM}
	enum Approach{NONE,EAGLE,PARAM}
	
	static Map<String, ContextKind> contextKinds = new HashMap<>();
	static Map<String, Approach> approaches = new HashMap<>();
	static Pattern pattern;
	static{
		add(contextKinds, ContextKind.INSENS,"insensitive", "insens", "ci");
		add(contextKinds, ContextKind.CALLSITE,"callsite", "call", "c");
		add(contextKinds, ContextKind.OBJECT,"object", "obj", "o");
		add(contextKinds, ContextKind.TYPE,"type", "t");
		add(contextKinds, ContextKind.PARAM,"param","p");

		add(approaches, Approach.EAGLE,"eagle", "e");
		add(approaches,Approach.PARAM,"param","p");
		
		StringBuilder regexPattern=new StringBuilder();
		regexPattern.append("^");
		regexPattern.append("(("+String.join("|", approaches.keySet())+")-)?"); // 2/2
		regexPattern.append("(\\d*)("+String.join("|", contextKinds.keySet())+")(\\+?(\\d*)h(eap)?)?"); // 1,2,4/5
		regexPattern.append("$");
		
		pattern = Pattern.compile(regexPattern.toString());
	}
	
	static <T> void add(Map<String, T> approachesOrPTATypes, T name, String... aliases){
		for(String alias:aliases)
			approachesOrPTATypes.put(alias, name);
	}
	
	private ContextKind type;
	private Approach approach;
	private int k, hk;
	//选择context的产生方法
	public boolean isInsensitive(){
		return ContextKind.INSENS==type;
	}
	public boolean isCallSiteSens(){
		return ContextKind.CALLSITE==type;
	}
	public boolean isObjSens(){
		return ContextKind.OBJECT==type;
	}
	public boolean isTypeSens(){
		return ContextKind.TYPE==type;
	}
	public boolean isParamSens(){
		return ContextKind.PARAM==type;
	}
	//main中选择eagle还是param
	public boolean isEagle(){
		return Approach.EAGLE==approach;
	}
	public boolean isParam(){
		return Approach.PARAM==approach;
	}
	
	public int getContextDepth(){
		return k;
	}
	public int getHeapContextDepth(){
		return hk;
	}
	
	public PTAPattern(String ptacmd) {
		Matcher matcher = pattern.matcher(ptacmd);
		if (!matcher.find())
			throw new RuntimeException("Unsupported PTA: "+ptacmd+" !");
		String approachString=matcher.group(2);
		String kString = matcher.group(3);
		String typeString=matcher.group(4);
		String hkString = matcher.group(6);
		
		approach = approachString==null?Approach.NONE:approaches.get(approachString);
		k = kString.equals("")?1:Integer.parseInt(kString);
		type = typeString==null?ContextKind.INSENS:contextKinds.get(typeString);
		hk = hkString==null?-1:hkString.equals("")?1:Integer.parseInt(hkString);
		
		if(k==0)
			type=ContextKind.INSENS;
		if(isInsensitive())
			return;
		if(isEagle())
			if(isCallSiteSens())
				throw new RuntimeException("This approach is currently not designed for call-site sensitivity.");
		
		if(isCallSiteSens()){
			if(hk==-1)
				hk=k-1;
			else if(hk>k)
				throw new RuntimeException("Heap context depth cannot exceed method context depth!");
		}else {
			if(hk==-1)
				hk=k-1;
			else if(hk>k||hk<k-1)
				throw new RuntimeException("Heap context depth can only be k or k-1 for this kind of analysis!");
		}
	}
	
	@Override
	public String toString() {
		if(isInsensitive())
			return "insensitive";
		StringBuilder stringBuilder = new StringBuilder();
		switch (approach) {
		case EAGLE:
			stringBuilder.append("eagle-");
			break;
		default:
			break;
		}
		stringBuilder.append(k);
		switch (type) {
		case CALLSITE:
			stringBuilder.append("callsite");
			break;
		case OBJECT:
			stringBuilder.append("object");
			break;
		case TYPE:
			stringBuilder.append("type");
			break;
		default:
			break;
		}
		stringBuilder.append('+');
		stringBuilder.append(hk);
		stringBuilder.append("heap");
		return stringBuilder.toString();
	}
}
