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
 *  that the thread owning that stage will first removed the job from the database,
 *  then process it, then either restore that job in the event of a failure, or create
 *  a job for the next stage and install it into the database.
 * Workflow:
 *  Extract subtitle->OCR->transcode->move files to final destinations->update probe info
 * Parallel workflow:
 *  Make fake MKVs->update probe info.
 * @author Dan
 */
public class WorkflowOrchestrator
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;
	private final String logFileName = "log_workflow_orchestrator.txt" ;
	private transient List< WorkflowStageThread > threadList = new ArrayList< WorkflowStageThread >() ;
	private final transient String stopFileName = "C:\\Temp\\stop_workflow.txt" ;
	private transient MoviesAndShowsMongoDB masMDB = null ;

	public WorkflowOrchestrator()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
	
		setupThreads() ;
	}

	private void setupThreads()
	{
		WorkflowStageThread_MakeFakeMKVFiles makeFakeMKVFilesThread = new WorkflowStageThread_MakeFakeMKVFiles(
				"makeFakeMKVFileThread", log, common, masMDB ) ;
		threadList.add( makeFakeMKVFilesThread ) ;
		WorkflowStageThread_ProbeFile probeFileThread = new WorkflowStageThread_ProbeFile(
				"probeFileThread", log, common, masMDB ) ;
		threadList.add( probeFileThread ) ;
		WorkflowStageThread_UpdateCorrelatedFile updatedCorrelatedFileThread = new WorkflowStageThread_UpdateCorrelatedFile(
				"updateCorrelatedFileThread", log, common, masMDB ) ;
		threadList.add( updatedCorrelatedFileThread ) ;
		WorkflowStageThread_TranscodeMKVFiles transcodeMKVFilesThread = new WorkflowStageThread_TranscodeMKVFiles(
				"transcodeMKVFilesThread", log, common, masMDB ) ;
		threadList.add( transcodeMKVFilesThread ) ;
	}

	public static void main(String[] args)
	{
		WorkflowOrchestrator wfo = new WorkflowOrchestrator() ;
		wfo.runThreads() ;
	}

	public void runThreads()
	{
		// Only start threads if execution is permitted
		if( !common.shouldStopExecution( getStopFileName() ) )
		{
			log.info( "Starting threads..." ) ;
			for( WorkflowStageThread theThread : threadList )
			{
				log.info( "Starting thread " + theThread.toString() + "..." ) ;
				theThread.start() ;
			}
			log.info( "Started " + threadList.size() + " thread(s)" ) ;
		}
		else
		{
			log.info( "Stop execution indicator found" ) ;
		}

		while( !common.shouldStopExecution( getStopFileName() ) )
		{
			try
			{
				// Anything for this thread to do?
				// TODO: Maybe add updated thread execution status or something, number commands, etc.
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

	public String getStopFileName()
	{
		return stopFileName ;
	}

}
