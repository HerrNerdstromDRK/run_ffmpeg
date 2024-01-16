package run_ffmpeg;

import java.util.logging.Logger;

public abstract class run_ffmpegWorkerThread extends Thread
{
	protected transient Logger log = null ;
	protected transient Common common = null ;
	
	private boolean workInProgress = false ;
	
	public run_ffmpegWorkerThread( Logger log,
			Common common )
	{
		assert( log != null ) ;
		assert( common != null ) ;
		
		this.log = log ;
		this.common = common ;
	}

//	public boolean hasMoreWork()
//	{
//		return this.isAlive() ;
//	}
	
	public boolean isWorkInProgress()
	{
		return workInProgress ;
	}

	public void setWorkInProgress( boolean workInProgress )
	{
		this.workInProgress = workInProgress ;
	}
	
	/**
	 * The default and desired behavior is for the worker threads to ask their controller threads for shouldKeepRunning().
	 * @return
	 */
	public abstract boolean shouldKeepRunning() ;
	
//	public boolean shouldKeepRunning()
//	{
//		return (isWorkInProgress() || hasMoreWork()) ;
//	}
	
}
