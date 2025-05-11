package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

/**
 * The purpose of this class is to run both the extract subtitles and OCR workflows
 *  simultaneously. The thinking is that the first uses primarily network and HDD,
 *  and the second uses primarily CPU, so they should be able to run in parallel.
 * It extends the controller thread superclass with a worker thread class. The worker
 *  here is useless -- it is just a means to gain the services of the underlying
 *  run_ffmpegControllerThreadTemplate hierarchy.
 * Instances of this class will create an ExtractSubtitle instance and OCRSubtitle
 *  instance and run synchronously in turn.
 */
public class ExtractAndOCRSubtitles extends run_ffmpegControllerThreadTemplate< ExtractAndOCRSubtitlesWorkerThread >
{
	private transient ExtractSubtitles extractSubtitles = null ; 
	private transient OCRSubtitles ocrSubtitles = null ;
	
	/// The structure used to pass files that have had their subtitles successfully extracted
	/// to the ocrThread to OCR them.
	/// Extract subtitles->OCR subtitles
	private PriorityBlockingQueue< File > filePipeline = new PriorityBlockingQueue< File >( 25, new FileSortLargeToSmall() ) ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_extract_and_ocr.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_extract_and_ocr.txt" ;

	public ExtractAndOCRSubtitles()
	{
		super( logFileName, stopFileName ) ;
		initObject() ;
	}

	public ExtractAndOCRSubtitles( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		initObject() ;
	}

	protected void initObject()
	{
		extractSubtitles = new ExtractSubtitles( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		extractSubtitles.setTranscodePipeline( filePipeline ) ;
		extractSubtitles.setName( "extractSubtitles" ) ;
		
		ocrSubtitles = new OCRSubtitles( log, common, stopFileName, masMDB, probeInfoCollection ) ;
		ocrSubtitles.setFilesToOCR( filePipeline ) ;
		ocrSubtitles.setName( "ocrSubtitles" ) ;
	}

	public static void main( String[] args )
	{
		ExtractAndOCRSubtitles controllerThread = new ExtractAndOCRSubtitles() ;
		controllerThread.setName( "ExtractAndOCRSubtitles" ) ;
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

		// Build the list of folders to extract and OCR
		List< String > foldersToExtractAndOCR = new ArrayList< String >() ;
//		foldersToExtractAndOCR.addAll( Common.getAllMediaFolders() ) ;
//		foldersToExtractAndOCR.add( Common.getPathToTVShows()) ;
//		foldersToExtractAndOCR.add( Common.getPathToMovies() ) ;
//		foldersToExtractAndOCR.add( Common.getPathToOCRInputDirectory() ) ;
//		foldersToExtractAndOCR.add( "D:\\temp\\To OCR") ;
//		foldersToExtractAndOCR.add( "E:\\To OCR") ;
//		foldersToExtractAndOCR.add( "\\\\skywalker\\Media\\Movies" ) ;
		foldersToExtractAndOCR.add( "\\\\skywalker\\Media\\TV_Shows\\Archer 2 (2003)" ) ;

		// Pass the list of folders to the extract object
		extractSubtitles.addFoldersToExtract( foldersToExtractAndOCR ) ;
		
		// Also pass the list of folders to the ocr object since those folders may have existing .sup files to ocr
		ocrSubtitles.addFoldersToOCR( foldersToExtractAndOCR ) ;
		
		log.info( "Running extract and OCR on " + foldersToExtractAndOCR.size() + " folder(s): " + foldersToExtractAndOCR.toString() ) ;
	}
	
	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	@Override
	protected List< ExtractAndOCRSubtitlesWorkerThread > buildWorkerThreads()
	{
		List< ExtractAndOCRSubtitlesWorkerThread > threads = new ArrayList< ExtractAndOCRSubtitlesWorkerThread >() ;

		// Encapsulate the extract and ocr objects in a separate thread and use the base controller class services
		// to manage them
		ExtractAndOCRSubtitlesWorkerThread extractWorkerThread = new ExtractAndOCRSubtitlesWorkerThread( log, common, this, extractSubtitles, null ) ;
		ExtractAndOCRSubtitlesWorkerThread ocrWorkerThread = new ExtractAndOCRSubtitlesWorkerThread( log, common, this, null, ocrSubtitles ) ;
		
		// In order for single threaded mode to work properly, the pipeline worker thread names must be in the right order
		// in the underlying threadMap. That *should* mean to alphabetize them according to their position in the pipeline.
		extractWorkerThread.setName( "extractWorkerThread" ) ;
		ocrWorkerThread.setName( "ocrWorkerThread" ) ;
		
		threads.add( extractWorkerThread ) ;
		threads.add( ocrWorkerThread ) ; 
		
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
		
		// Wait for when all OCR work is done.
		if( extractSubtitles.pipelineIsEmpty() && (0 == ocrSubtitles.countActiveOCR()) )
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
		super.stopRunning() ;
	}
}
