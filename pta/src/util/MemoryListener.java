package util;

import java.util.Timer;
import java.util.TimerTask;

public class MemoryListener extends Timer{
	static final int KB = 1024,MB = KB*1024, GB = MB*1024;
	long usedbefore;
	long maxUsed;
	Runtime runtime = Runtime.getRuntime();
	private TimerTask task;
	public void listen(){
		for(int i=0;i<5;i++)
			System.gc();
		usedbefore = runtime.totalMemory()-runtime.freeMemory();
		task = new TimerTask(){
			@Override
			public void run() {
				long currentUsed = runtime.totalMemory()-runtime.freeMemory();
				if(currentUsed>maxUsed)
					maxUsed = currentUsed;
			}
		};
		schedule(task, 0, 100);
	}
	public long stop(String info, String unit){
		task.cancel();
		long ret = maxUsed-usedbefore;
		int u = unit.equalsIgnoreCase("KB")?KB:unit.equalsIgnoreCase("MB")?MB:unit.equalsIgnoreCase("GB")?GB:1;
		System.out.println(info+":"+ ret/u + unit);
		return ret;
	}
}