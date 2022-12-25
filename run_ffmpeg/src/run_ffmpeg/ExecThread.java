package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;

public class ExecThread extends Thread {

	private List< ThreadAction > execList = new ArrayList< >() ;

	private boolean keepRunning = true ;

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
			run_ffmpeg.out( "ExecThread> Shutting down after executing " + numCommands + " command(s)" ) ;
		}
		catch( Exception theException )
		{
			run_ffmpeg.out( "ExecThread.run> Exception: " + theException ) ;
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
		run_ffmpeg.out( "ExecThread> Added work: " + addMe ) ;
	}

	public synchronized void stopRunning()
	{
		keepRunning = false ;
	}

}
