package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ExecThread extends Thread
{
	private String threadName = "Unnamed Thread" ;
	private List< ThreadAction > execList = new ArrayList< >() ;
	private transient Logger log = null ;
//	private transient Common common = null ;

	private boolean keepRunning = true ;

	public ExecThread( String threadName, Common common, Logger log )
	{
		this.threadName = threadName ;
//		this.common = common ;
		this.log = log ;
	}

	@Override
	public void run()
	{
		try
		{
			int numCommands = 0 ;
			while( keepRunning )
			{
				if( !hasMoreWork() )
				{
					Thread.sleep( 100 ) ;
				}

				ThreadAction execMe = null ;

				synchronized( execList )
				{
					if( !execList.isEmpty() )
					{
						// Got something to do!
						execMe = execList.get( 0 ) ;
						execList.remove( 0 ) ;
					} // if( !isEmpty() )
				} // synchronized

				if( execMe != null )
				{
					execMe.doAction() ;
					++numCommands ;
				}
			} // while( keepRunning )
			log.info( toString() + " Shutting down after executing " + numCommands + " command(s)" ) ;
		}
		catch( Exception theException )
		{
			log.info( toString() + " Exception: " + theException ) ;
		}
	}

	public boolean hasMoreWork()
	{
		boolean hasWork = false ;
		synchronized( execList )
		{
			if( !execList.isEmpty() )
			{
				hasWork = true ;
			}
			// No need to worry about a command that is currently executing as it will continue to execute
			// even if the thread is shutdown
		}
		return hasWork ;
	}
	
	public void addWork( ThreadAction addMe )
	{
		synchronized( execList )
		{
			execList.add( addMe ) ;
		}
		log.info( toString() + " Added work: " + addMe ) ;
	}

	public synchronized void stopRunning()
	{
		keepRunning = false ;
	}
	
	public String toString()
	{
		return getThreadName() ;
	}
	
	public String getThreadName()
	{
		return threadName ;
	}

}
