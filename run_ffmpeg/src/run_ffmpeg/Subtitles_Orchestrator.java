package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * The purpose of this class is to run both the extract subtitles and convert (OCR & transcribe) workflows
 *  simultaneously. The thinking is that the first uses primarily network and HDD,
 *  and the second uses primarily CPU, so they should be able to run in parallel.
 * It extends the controller thread superclass with a worker thread class. The worker
 *  here is useless -- it is just a means to gain the services of the underlying
 *  run_ffmpegControllerThreadTemplate hierarchy.
 * Instances of this class will create an ExtractSubtitle instance, Subtitles_OCR, and Subtitles_Transcribe 
 *  instances and run synchronously in turn.
 */
public class Subtitles_Orchestrator extends run_ffmpegControllerThreadTemplate< Subtitles_OrchestratorWorkerThread >
{
	private transient Subtitles_Extract extractSubtitles = null ; 
	private transient Subtitles_OCR ocrSubtitles = null ;
	private transient Subtitles_Transcribe transcribeSubtitles = null ;
	
	/// The structure used to pass files that have had their subtitles successfully extracted
	/// to the ocrThread to OCR them.
	/// Extract subtitles->OCR subtitles
	private PriorityBlockingQueue< File > ocrFilePipeline = new PriorityBlockingQueue< File >( 25, new FileSortLargeToSmall() ) ;
	private PriorityBlockingQueue< File > transcriptionFilePipeline = new PriorityBlockingQueue< File >( 25, new FileSortLargeToSmall() ) ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_subtitles_orchestrator.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_subtitles_orchestrator.txt" ;

	public Subtitles_Orchestrator()
	{
		super( logFileName, stopFileName ) ;
		initObject() ;
	}

	public Subtitles_Orchestrator( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpeg_ProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		initObject() ;
	}

	protected void initObject()
	{
		extractSubtitles = new Subtitles_Extract( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		extractSubtitles.setOCRPipeline( ocrFilePipeline ) ;
		extractSubtitles.setTranscriptionPipeline( transcriptionFilePipeline ) ;
		extractSubtitles.setName( "extractSubtitles" ) ;
		
		ocrSubtitles = new Subtitles_OCR( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		ocrSubtitles.setFilesToOCR( ocrFilePipeline ) ;
		ocrSubtitles.setName( "ocrSubtitles" ) ;
		
		transcribeSubtitles = new Subtitles_Transcribe( log, common, masMDB, probeInfoCollection ) ;
		transcribeSubtitles.setFilesToTranscribe( transcriptionFilePipeline ) ;
		transcribeSubtitles.setName( "transcribeSubtitles" ) ;
	}

	public static void main( String[] args )
	{
		Subtitles_Orchestrator controllerThread = new Subtitles_Orchestrator() ;
		controllerThread.setName( "Subtitles_Orchestrator" ) ;
		controllerThread.Init() ;
		controllerThread.Execute() ;
		System.out.println( "main> Shutdown." ) ;
	}
	
	/**
	 * Override the method called when execution begins.
	 */
	@Override
	public void Init()
	{
		setUseThreads( true ) ;
		common.setTestMode( false ) ;

		extractSubtitles.setUseThreads( isUseThreads() ) ;
		ocrSubtitles.setUseThreads( isUseThreads() ) ;
		transcribeSubtitles.setUseThreads( isUseThreads() ) ;

		// Build the list of folders to extract and OCR
//		List< String > foldersToExtractAndOCR = new ArrayList< String >() ;
//		extractSubtitles.addFoldersToExtractWithNewThread( Common.getAllMediaFolders() ) ;
//		extractSubtitles.addFoldersToExtractWithNewThread( Common.getPathToTVShows() ) ;
//		extractSubtitles.addFoldersToExtractWithNewThread( Common.getPathToMovies() ) ;
		extractSubtitles.addFoldersToExtractWithNewThread( Common.getPathToToOCR() ) ;
//		extractSubtitles.addFoldersToExtractWithNewThread( "\\\\skywalker\\\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}\\Season 02" ) ;

		// Also pass the list of folders to the convert objects since those folders may have existing .sup/.wav files to convert
		ocrSubtitles.addFoldersToOCR( extractSubtitles.getAllFoldersToExtract() ) ;
		transcribeSubtitles.addFoldersToTranscribe( extractSubtitles.getAllFoldersToExtract() ) ;

//		log.info( "Running extract and OCR on " + foldersToExtractAndOCR.size() + " folder(s): " + foldersToExtractAndOCR.toString() ) ;
	}
	
	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	@Override
	protected List< Subtitles_OrchestratorWorkerThread > buildWorkerThreads()
	{
		List< Subtitles_OrchestratorWorkerThread > threads = new ArrayList< Subtitles_OrchestratorWorkerThread >() ;

		// Encapsulate the extract and ocr objects in a separate thread and use the base controller class services
		// to manage them
		Subtitles_OrchestratorWorkerThread extractWorkerThread = new Subtitles_OrchestratorWorkerThread( log, common, this, extractSubtitles, null, null ) ;
		Subtitles_OrchestratorWorkerThread ocrWorkerThread = new Subtitles_OrchestratorWorkerThread( log, common, this, null, ocrSubtitles, null ) ;
		Subtitles_OrchestratorWorkerThread transcribeWorkerThread = new Subtitles_OrchestratorWorkerThread( log, common, this, null, null, transcribeSubtitles ) ;
		
		// In order for single threaded mode to work properly, the pipeline worker thread names must be in the right order
		// in the underlying threadMap. That *should* mean to alphabetize them according to their position in the pipeline.
		extractWorkerThread.setName( "extractWorkerThread" ) ;
		ocrWorkerThread.setName( "ocrWorkerThread" ) ;
		transcribeWorkerThread.setName( "transcribeWorkerThread" ) ;
		
		threads.add( extractWorkerThread ) ;
		threads.add( ocrWorkerThread ) ; 
		threads.add( transcribeWorkerThread ) ;
		
		// This object runs two separate controller object. No need to build threads -- each of the subordinate
		// controller objects manages its own threads.
		return threads ;
	}

	/**
	 * Overload this method because the default check will be on the extract and ocr threads, but ignore the
	 * subordinate worker threads in this object.
	 */
	@Override
	protected boolean atLeastOneThreadIsAlive()
	{
		return (extractSubtitles.atLeastOneThreadIsAlive() || ocrSubtitles.atLeastOneThreadIsAlive()) ;
	}
	
	/**
	 * Look for the condition that the ExtractSubtitles worker thread is dead and the OCRSubtitlesWorkerThread is done with all work.
	 */
	@Override
	public void Execute_mainLoopEnd()
	{
		if( extractSubtitles.atLeastOneThreadIsAlive() )
		{
			// Still extracting.
			return ;
		}
		// Post-condition: Done extracting.
		
		// Wait until all ocr and transcribe work is done.
		if( extractSubtitles.pipelineIsEmpty() && (0 == ocrSubtitles.countActiveOCR()) && (0 == transcribeSubtitles.countActiveTranscriptions()) )
		{
			stopRunning() ;
		}
	}
	
	/**
	 * Notify the subordinate threads to stop running.
	 */
	@Override
	public void stopRunning()
	{
		extractSubtitles.stopRunning() ;
		ocrSubtitles.stopRunning() ;
		transcribeSubtitles.stopRunning() ;
		super.stopRunning() ;
	}
}
