package run_ffmpeg;

import java.util.logging.Logger;

public class WorkflowStageThread extends Thread
{
	private String threadName = null ;
	protected transient Logger log = null ;
	protected transient Common common = null ;
	protected transient MoviesAndShowsMongoDB masMDB = null ;
	private boolean keepRunning = true ;

	public WorkflowStageThread( final String threadName, Logger log, Common common, MoviesAndShowsMongoDB masMDB )
	{
		this.threadName = threadName ;
		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
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
}
