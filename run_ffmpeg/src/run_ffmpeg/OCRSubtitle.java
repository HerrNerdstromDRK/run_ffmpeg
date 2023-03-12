package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

/**
 * Execute OCR on image-based subtitle files.
 * @author Dan
 */
public class OCRSubtitle extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	private transient Common common = null ;
	private transient boolean keepThreadRunning = true ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_ocr_subtitle.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_ocr_subtitle.txt" ;

	/// The list of filenames to OCR
	/// Will be used as the job queue.
	private List< String > fileNamesToOCR = new ArrayList< String >() ;

	/// The extensions that contain image-based subtitles
	private String[] extensionsToOCR = { ".sup" } ;

	public OCRSubtitle()
	{
		log = Common.setupLogger( getLogFileName(), this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		OCRSubtitle ocrs = new OCRSubtitle() ;
		ocrs.runWithThreads() ;
	}

	public void runWithThreads()
	{
		final int numThreads = 3 ;

		// Retrieve all of the drives and folders containing mkv files to find .sup files
		List< String > drivesAndFoldersToOCR = new ArrayList< String >() ;
		drivesAndFoldersToOCR.addAll( common.getAllMKVDrives() ) ;
		log.info( "Running OCR on: " + drivesAndFoldersToOCR.toString() ) ;

		// Walk through each drive and folder to find the .sup files
		for( String folderToOCR : drivesAndFoldersToOCR )
		{
			List< File > filesToOCR = common.getFilesInDirectoryByExtension( folderToOCR, getExtensionsToOCR() ) ;
			for( File fileToOCR : filesToOCR )
			{
				// Add each file to ocr to a list for later processing.
				final String fileNameToOCR = fileToOCR.getAbsolutePath() ;
				addFileNameToOCR( fileNameToOCR ) ;
			}
		}
		log.info( "Will execute OCR on " + fileNamesToOCR.size() + " file(s) using " + numThreads + " thread(s)" ) ;

		// Create and start the threads.
		List< OCRSubtitle > ocrThreads = new ArrayList< OCRSubtitle >() ;
		for( int i = 0 ; i < numThreads ; ++i )
		{
			OCRSubtitle ocrs = new OCRSubtitle() ;
			
			// This is a bit confusing to visualize, but we need to pass along
			// the file names and extensions to OCR to each subordinate thread.
			// All threads, including this controller thread, will reference the same
			// work queue.
			ocrs.setFileNamesToOCR( getFileNamesToOCR() );
			ocrs.setExtensionsToOCR( getExtensionsToOCR() ) ;
			ocrThreads.add( ocrs ) ;

			// Upon starting the thread, it will begin pulling ocr jobs from the queue and continue
			// until the queue is empty or the controller thread tells it to stop.
			ocrs.start() ;
		}

		// This loop is for the controller thread.
		// The below calls to stopRunningThread() are used to shutdown each individual thread.
		Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
		while( shouldKeepRunning() && ocrJobAvailable() )
		{
			try
			{
				// Sleep while we should keep running at at least one thread is busy working.
				Thread.sleep( 100 ) ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception: " + theException.toString() ) ;
			}
		}

		// Tell the threads to shut down.
		log.info( "Shutting down threads..." ) ;
		for( OCRSubtitle theoCRSubtitleObject : ocrThreads )
		{
			theoCRSubtitleObject.stopRunningThread() ;
		}

		// Wait for them to shut down.
		try
		{
			for( OCRSubtitle theoCRSubtitleObject : ocrThreads )
			{
				theoCRSubtitleObject.join() ;
			}
		}
		catch( Exception theException )
		{
			log.warning( "Exception: " + theException.toString() ) ;
		}
		log.info( "Shut down." ) ;
	}

	/**
	 * An individual thread loop.
	 */
	@Override
	public void run()
	{
		log.info( "New thread reporting for duty." ) ;
		while( isKeepThreadRunning() )
		{
			// Get a file to OCR from the queue.
			final String fileNameToOCR = getFileNameToOCR() ;
			if( null == fileNameToOCR )
			{
				// Out of jobs. Exit this thread.
				log.info( "No more jobs for this thread." ) ;
				return ;
			}

			// OCR this file.
			boolean commandSuccess = doOCRFileName( fileNameToOCR ) ;
			if( commandSuccess )
			{
				// If the OCR was successful, then delete the input (.sup) file.
				log.info( "Deleting OCR input file: " + fileNameToOCR ) ;
				if( !common.getTestMode() )
				{
					File fileToOCR = new File( fileNameToOCR ) ;
					fileToOCR.delete() ;
				}
			}
			log.info( "Completed OCR on file: " + fileNameToOCR ) ;
		}
		log.info( "Thread is shutdown." ) ;
	}

	public void addFileNameToOCR( final String newFileNameToOCR )
	{
		fileNamesToOCR.add( newFileNameToOCR ) ;
	}

	public void addFileNamesToOCR( final List< String > newFileNamesToOCR )
	{
		fileNamesToOCR.addAll( newFileNamesToOCR ) ;
	}

	/**
	 * Run OCR on a single file. Delete the original file when complete.
	 * @param fileNameToOCR
	 */
	public boolean doOCRFileName( final String fileNameToOCR )
	{
		log.info( "Running OCR on file: " + fileNameToOCR ) ;
		// Format is:
		// dotnet PgsToSrt.dll --input video1.fr.sup --output video1.fr.srt --tesseractlanguage fra --tesseractdata path_to_language_files
		// The output filename will default, as will the language file.
		// However, will need to include path to the tessdata
		ImmutableList.Builder< String > ocrExecuteCommand = new ImmutableList.Builder<String>() ;
		ocrExecuteCommand.add( Common.getPathToDotNet(), Common.getPathToPgsToSrtDLL() ) ;
		ocrExecuteCommand.add( "--input", fileNameToOCR ) ;
		ocrExecuteCommand.add( "--tesseractdata", Common.getPathToTessdata() ) ;

		boolean commandSuccess = common.executeCommand( ocrExecuteCommand ) ;
		log.info( "OCR on file " + fileNameToOCR + ": " + commandSuccess ) ;
		return commandSuccess ;
	}

	/**
	 * Return true if at least one job is available.
	 */
	public synchronized boolean ocrJobAvailable()
	{
		synchronized( fileNamesToOCR )
		{
			return !fileNamesToOCR.isEmpty() ;
		}
	}

	public String[] getExtensionsToOCR() {
		return extensionsToOCR;
	}
	
	public void setExtensionsToOCR( String[] extensionsToOCR )
	{
		this.extensionsToOCR = extensionsToOCR ;
	}

	/**
	 * Returns the next filename to OCR, or null if none available.
	 * @return
	 */
	public synchronized String getFileNameToOCR()
	{
		String fileNameToOCR = null ;
		synchronized( fileNamesToOCR )
		{
			if( !fileNamesToOCR.isEmpty() )
			{
				fileNameToOCR = fileNamesToOCR.remove( 0 ) ;
			}
		}
		return fileNameToOCR ;
	}

	public String getLogFileName() {
		return logFileName;
	}

	public String getStopFileName() {
		return stopFileName;
	}

	public boolean isKeepThreadRunning() {
		return keepThreadRunning;
	}

	public void setKeepThreadRunning(boolean keepThreadRunning) {
		this.keepThreadRunning = keepThreadRunning;
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( stopFileName ) ;
	}

	public void stopRunningThread()
	{
		setKeepThreadRunning( false ) ;
	}

	/**
	 * Return true so long as at least one thread is alive (i.e., working).
	 * @param ocrThreads
	 * @return
	 */
	public boolean threadsAreAlive( final List< OCRSubtitle > ocrThreads )
	{
		for( OCRSubtitle theOCRSubtitleThread : ocrThreads )
		{
			// As long as a single thread is alive, then return true.
			if( theOCRSubtitleThread.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}
	
	public void setFileNamesToOCR(List<String> fileNamesToOCR) {
		this.fileNamesToOCR = fileNamesToOCR;
	}

	public List<String> getFileNamesToOCR() {
		return fileNamesToOCR;
	}
}
