package run_ffmpeg;

import java.util.logging.Logger;

public class WorkflowStageThread extends Thread
{
	private String threadName = null ;
	protected transient Logger log = null ;
	protected transient Common common = null ;
	protected MoviesAndShowsMongoDB masMDB = null ;
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
				doAction() ;
				Thread.sleep( 1000 ) ;
			}
			catch( Exception e )
			{
				log.warning( getThreadName() + " Exception: " + e.toString() ) ;
			}
		}
		log.info( getThreadName() + " shut down." ) ;
	}

	public void doAction()
	{
		log.warning( "Base class doAction called" ) ;		
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


}
