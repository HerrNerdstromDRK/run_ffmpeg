package run_ffmpeg;

import java.util.logging.Logger;

public class WorkflowStageThread extends Thread
{
	private String threadName = null ;
	protected transient Logger log = null ;
	protected transient Common common = null ;
	protected transient MoviesAndShowsMongoDB masMDB = null ;
	private boolean keepRunning = true ;
	
	/// true if work is currently in progress, false otherwise.
	/// Important for subclasses to use this variable as it communicates to the orchestrator if the thread should be counted as part of
	///  the idle timeout checks.
	protected boolean workInProgress = false ;
	
	/// The time at which, in ms, an action was performed.
	protected long timeLastWorkAccomplished = 0 ;

	public WorkflowStageThread( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		this.threadName = threadName ;
		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
		setTimeLastWorkAccomplished( System.currentTimeMillis() ) ;
	}

	@Override
	public void run()
	{
		while( doKeepRunning() )
		{
			try
			{
				if( !doAction() )
				{
					// If no work was completed, then sleep. Otherwise, proceed immediately to the next action.
					Thread.sleep( 100 ) ;
				}
				else
				{
					// Some work was done.
					setTimeLastWorkAccomplished( System.currentTimeMillis() ) ;
				}
			}
			catch( Exception e )
			{
				log.warning( getThreadName() + " Exception: " + e.toString() ) ;
			}
		}
		log.info( getThreadName() + " shut down." ) ;
	}

	/**
	 * Override this method. This method performs the work intended for subclass instances of this class.
	 * @return true if work completed false if no work completed.
	 */
	public boolean doAction()
	{
		log.warning( "Base class doAction called" ) ;
		return false ;
	}

	public boolean doKeepRunning()
	{
		return keepRunning ;
	}

	public void stopRunning()
	{
		keepRunning = false ;
	}

	public String getThreadName()
	{
		return threadName ;
	}

	public String toString()
	{
		return getThreadName() ;
	}

	public String getUpdateString()
	{
		return "" ;
	}

	public long getTimeLastWorkAccomplished()
	{
		return timeLastWorkAccomplished ;
	}

	public void setTimeLastWorkAccomplished( final long timeLastWorkAccomplished )
	{
		this.timeLastWorkAccomplished = timeLastWorkAccomplished ;
	}

	public boolean isWorkInProgress()
	{
		return workInProgress ;
	}

	public void setWorkInProgress( final boolean workInProgress )
	{
		this.workInProgress = workInProgress ;
	}
}
