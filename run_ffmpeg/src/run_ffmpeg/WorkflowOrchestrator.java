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
	protected long infoFrequency = 10000 ;
	protected long lastInfoUpdate = 0 ;
	protected int numOCRThreads = 2 ;

	public WorkflowOrchestrator()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB( log ) ;

		setupThreads() ;
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

		log.info( "Starting threads..." ) ;
		for( WorkflowStageThread theThread : threadList )
		{
			log.info( "Starting thread " + theThread.toString() + "..." ) ;
			theThread.start() ;
		}
		log.info( "Started " + threadList.size() + " thread(s)" ) ;

		while( !common.shouldStopExecution( getStopFileName() ) )
		{
			try
			{
				// Anything for this thread to do?
				if( timeToUpdateStatus() )
				{
					logUpdate() ;
					setLastInfoUpdate( System.currentTimeMillis() ) ;
				}
				
				Thread.sleep( 100 ) ;
			}
			catch( Exception e )
			{
				log.warning( "Exception: " + e.toString() ) ;
			}
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

	protected void logUpdate()
	{
		try
		{
			for( WorkflowStageThread theThread : threadList )
			{
				String threadInfo = theThread.getName() + " " ;
				threadInfo += (theThread.getState() == Thread.State.TIMED_WAITING) ? "(SLEEP)" : "ACTIVE" ;
				
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

	public String getStopFileName()
	{
		return stopFileName ;
	}

	public int getNumOCRThreads()
	{
		return numOCRThreads ;
	}

	public void setNumOCRThreads( final int numOCRThreads )
	{
		this.numOCRThreads = numOCRThreads ;
	}

	public long getInfoFrequency()
	{
		return infoFrequency ;
	}

	public void setInfoFrequency( final long infoFrequency )
	{
		this.infoFrequency = infoFrequency ;
	}

	public long getLastInfoUpdate()
	{
		return lastInfoUpdate ;
	}

	public void setLastInfoUpdate( final long lastInfoUpdate )
	{
		this.lastInfoUpdate = lastInfoUpdate ;
	}

}
