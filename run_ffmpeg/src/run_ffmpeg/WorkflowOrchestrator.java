package run_ffmpeg;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Instrument the workflow.
 * Works by retaining at least one worker thread for each function
 *  in the workflow.
 * The responsibility to initiate the next stage in the workflow lays
 *  with the thread that owns the previous state.
 * Workflow jobs are stored in the database.
 * Convention is herein established that, upon initiating a stage in the workflow,
 *  that the thread owning that stage will first remove the job from the database,
 *  then process it, then either restore that job in the event of a failure, or create
 *  a job for the next stage and install it into the database.
 * @author Dan
 */
public class WorkflowOrchestrator
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;
	protected transient MoviesAndShowsMongoDB masMDB = null ;

	private final String logFileName = "log_workflow_orchestrator.txt" ;
	private final transient String stopFileName = "C:\\Temp\\stop_workflow.txt" ;

	private transient List< WorkflowStageThread > threadList = new ArrayList< WorkflowStageThread >() ;

	/// The time, in ms, between updates to the console.
	protected final long infoFrequency = 10000 ;
	protected long lastInfoUpdate = 0 ;

	/// The time, in ms, of the last time any thread accomplished a unit of work.
	/// Default value is MAX_VALUE indicating no idle threads.
	protected long timeLastWorkAccomplished = 0 ;

	/// The max amount of time to wait for a single thread to become active.
	/// If all threads are idle for this amount of time (or higher), then the
	/// application will shutdown.
	protected final long maxIdleThreadTimeout = 20000 ;
	protected int numOCRThreads = 2 ;

	public WorkflowOrchestrator()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB( log ) ;

		setTimeLastWorkAccomplished( System.currentTimeMillis() ) ;
		setupThreads() ;
	}

	public static void main( final String[] args )
	{
		WorkflowOrchestrator wfo = new WorkflowOrchestrator() ;
		wfo.runThreads() ;
	}

	public void runThreads()
	{
		// Only start threads if execution is permitted
		if( common.shouldStopExecution( getStopFileName() ) )
		{
			return ;
		}

//		log.info( "Starting threads..." ) ;
		for( WorkflowStageThread theThread : threadList )
		{
			log.info( "Starting thread " + theThread.toString() + "..." ) ;
			theThread.start() ;
		}
		log.info( "Started " + threadList.size() + " thread(s)" ) ;

		while( !common.shouldStopExecution( getStopFileName() ) && !idleThreadsTimeout() )
		{
			try
			{
				// Anything for this thread to do?
				if( timeToUpdateStatus() )
				{
					logUpdate() ;
					setLastInfoUpdate( System.currentTimeMillis() ) ;
				}

				updateIdleThreadTimer() ;				
				Thread.sleep( 100 ) ;
			}
			catch( Exception e )
			{
				log.warning( "Exception: " + e.toString() ) ;
			}
		}

		if( common.shouldStopExecution( getStopFileName() ) )
		{
			log.info( "Shutting down due to presence of stop file: " + getStopFileName() ) ;
		}
		else if( idleThreadsTimeout() )
		{
			log.info( "Shutting down due to idle thread timeout of " + (getMaxIdleThreadTimeout() / 1000) + " seconds" ) ;
		}
		else
		{
			log.warning( "Shutting down for UNKNOWN reason" ) ;
		}
		
		// Shutdown the threads.
		log.info( "Stopping threads..." ) ;
		for( WorkflowStageThread theThread : threadList )
		{
			theThread.stopRunning() ;
		}
		log.info( "Stopped " + threadList.size() + " thread(s)" ) ;

		log.info( "Joining threads..." ) ;
		for( WorkflowStageThread theThread : threadList )
		{
			try
			{
				theThread.join() ;
			}
			catch( Exception e )
			{
				log.warning( "Exception stopping thread " + theThread.toString() + ": " + e.toString() ) ;
			}
			log.info( "Joined thread: " + theThread.toString() ) ;
		}
		log.info( "Joined " + threadList.size() + " thread(s)" ) ;
		log.info( "Program shut down complete." ) ;
	}

	public long getInfoFrequency()
	{
		return infoFrequency ;
	}

	public long getLastInfoUpdate()
	{
		return lastInfoUpdate ;
	}

	public long getMaxIdleThreadTimeout()
	{
		return maxIdleThreadTimeout ;
	}

	public int getNumOCRThreads()
	{
		return numOCRThreads ;
	}

	public String getStopFileName()
	{
		return stopFileName ;
	}

	public long getTimeLastWorkAccomplished()
	{
		return timeLastWorkAccomplished ;
	}

	/**
	 * Return true if all threads have been idle beyond the timeout limit; false otherwise.
	 * @return
	 */
	protected boolean idleThreadsTimeout()
	{
		final long currentTime = System.currentTimeMillis() ;
		final long durationOfIdleThreads = currentTime - getTimeLastWorkAccomplished() ;
	
		return (durationOfIdleThreads >= getMaxIdleThreadTimeout()) ;
	}

	protected void logUpdate()
	{
		try
		{
			for( WorkflowStageThread theThread : threadList )
			{
				String threadInfo = theThread.getName() + " " ;
				threadInfo += (theThread.getState() == Thread.State.TIMED_WAITING) ? "(SLEEP)" : "ACTIVE" ;
				threadInfo += ", workInProgress: " + theThread.isWorkInProgress() ;
				
				final String threadUpdateString = theThread.getUpdateString() ;
				if( !threadUpdateString.isBlank() ) threadInfo += ": " + theThread.getUpdateString() ;
	
				log.info( threadInfo ) ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
	}

	public void setLastInfoUpdate( final long lastInfoUpdate )
	{
		this.lastInfoUpdate = lastInfoUpdate ;
	}

	public void setNumOCRThreads( final int numOCRThreads )
	{
		this.numOCRThreads = numOCRThreads ;
	}

	public void setTimeLastWorkAccomplished( final long timeLastWorkAccomplished )
	{
		this.timeLastWorkAccomplished = timeLastWorkAccomplished ;
	}

	private void setupThreads()
	{
		//		WorkflowStageThread_ProbeFile probeFileThread = new WorkflowStageThread_ProbeFile(
		//				"probeFileThread", log, common, masMDB ) ;
		//		threadList.add( probeFileThread ) ;
		//		WorkflowStageThread_TranscodeMKVFiles transcodeMKVFilesThread = new WorkflowStageThread_TranscodeMKVFiles(
		//				"transcodeMKVFilesThread", log, common, masMDB ) ;
		//		threadList.add( transcodeMKVFilesThread ) ;
		WorkflowStageThread_SubtitleTranscribe transcribeThread = new WorkflowStageThread_SubtitleTranscribe(
				"AI", log, common, masMDB ) ;
		transcribeThread.setName( "AI" ) ;
		threadList.add( transcribeThread ) ;	
	
		for( int threadNum = 0 ; threadNum < getNumOCRThreads() ; ++ threadNum )
		{
			final String threadName = "OCR_" + threadNum ;
			WorkflowStageThread_SubtitleOCR ocrThread = new WorkflowStageThread_SubtitleOCR( threadName, log, common, masMDB ) ;
			ocrThread.setName( threadName ) ;
			threadList.add( ocrThread ) ;
		}
	}

	protected boolean timeToUpdateStatus()
	{
		final long currentTime = System.currentTimeMillis() ;
		final long timeSinceLastUpdate = currentTime - getLastInfoUpdate() ;
		if( timeSinceLastUpdate >= getInfoFrequency() )
		{
			return true ;
		}
		return false ;
	}

	/**
	 * Check the active/sleep status of each thread.
	 * If all threads are asleep, assume they are doing no work and update the timeSinceLastActiveThread, if not already
	 *  counting.
	 */
	protected void updateIdleThreadTimer()
	{
		if( !areAllThreadsIdle() )
		{
			// Work happening right now
			setTimeLastWorkAccomplished( System.currentTimeMillis() ) ;
		}
	}
	
	protected boolean areAllThreadsIdle()
	{
		for( WorkflowStageThread theThread : threadList )
		{
			if( theThread.isWorkInProgress() )
			{
				// This thread is NOT idle.
				return false ;
			}
		}
		return true ;
	}
}
