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

	/// Set to true to keep the instances running, set to false otherwise.
	/// This is meant to provide a programmatic way of shutting down all of the threads.
	private transient boolean keepThreadRunning = true ;

	/// The default number of threads to run.
	private int defaultNumThreads = 10 ;

	/// Duration, in milliseconds, between thread liveness checks.
	private long aliveCheckDuration = 5000 ;

	/// Set to true when the current thread is running an OCR; false otherwise
	private boolean activelyRunningOCR = false ;

	public synchronized boolean isActivelyRunningOCR()
	{
		return activelyRunningOCR ;
	}

	private synchronized void setActivelyRunningOCR( boolean activelyRunningOCR )
	{
		this.activelyRunningOCR = activelyRunningOCR;
	}

	/// File name to which to log activities for this application.
	private final String logFileName = "log_ocr_subtitle.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_ocr_subtitle.txt" ;

	/// The list of filenames to OCR
	/// Will be used as the job queue.
	private List< File > filesToOCR = new ArrayList< File >() ;

	/// The extensions that contain image-based subtitles
	private static String[] extensionsToOCR = { ".sup" } ;

	public OCRSubtitle()
	{
		log = Common.setupLogger( getLogFileName(), this.getClass().getName() ) ;
		common = new Common( log ) ;
	}

	public static void main( String[] args )
	{
		OCRSubtitle ocrs = new OCRSubtitle() ;
		List< String > drivesAndFoldersToOCR = new ArrayList< String >() ;
		Common common = ocrs.getCommon() ;
		common.setTestMode( false ) ;

		drivesAndFoldersToOCR.add( "d:\\temp" ) ;
		drivesAndFoldersToOCR.add( "N:\\To Convert - TV Shows" ) ;
		//		drivesAndFoldersToOCR.addAll( common.getAllMKVDrives() ) ;
		System.out.println( "main> Running on directories: " + drivesAndFoldersToOCR.toString() ) ;
		for( String directory : drivesAndFoldersToOCR )
		{
			List< File > filesToOCR = common.getFilesInDirectoryByExtension( directory, getExtensionsToOCR() ) ;
			ocrs.addFilesToOCR( filesToOCR ) ;
		}
		ocrs.runThreads() ;
	}

	public Common getCommon()
	{
		return common ;
	}

	public void addFileNameToOCR( final String newFileNameToOCR )
	{
		addFileToOCR( new File( newFileNameToOCR ) ) ;
	}

	public void addFileNamesToOCR( final List< String > newFileNamesToOCR )
	{
		for( String fileName : newFileNamesToOCR )
		{
			addFileToOCR( new File( fileName ) ) ;
		}
	}

	public void addFilesToOCR( final List< File > newFilesToOCR )
	{
		synchronized( filesToOCR )
		{
			filesToOCR.addAll( newFilesToOCR ) ;
		}
	}

	public void addFileToOCR( File newFileToOCR )
	{
		synchronized( filesToOCR )
		{
			filesToOCR.add( newFileToOCR ) ;
		}
	}

	public void addFoldersToOCR( List< String > folderPaths )
	{
		for( String theFolder : folderPaths )
		{
			List< File > filesToOCR = common.getFilesInDirectoryByExtension( theFolder, getExtensionsToOCR() ) ;
			addFilesToOCR( filesToOCR ) ;
		}
	}

	/**
	 * Run OCR on a single file. Delete the original file when complete.
	 * @param fileNameToOCR
	 */
	public boolean doOCRFile( final File fileToOCR )
	{
		log.info( "Running OCR on file: " + fileToOCR.toString() ) ;
		setActivelyRunningOCR( true ) ;
		// Format is:
		// dotnet PgsToSrt.dll --input video1.fr.sup --output video1.fr.srt --tesseractlanguage fra --tesseractdata path_to_language_files
		// The output filename will default, as will the language file.
		// However, will need to include path to the tessdata
		ImmutableList.Builder< String > ocrExecuteCommand = new ImmutableList.Builder<String>() ;
		ocrExecuteCommand.add( Common.getPathToDotNet(), Common.getPathToPgsToSrtDLL() ) ;
		ocrExecuteCommand.add( "--input", fileToOCR.getAbsolutePath() ) ;
		ocrExecuteCommand.add( "--tesseractdata", Common.getPathToTessdata() ) ;

		boolean commandSuccess = common.executeCommand( ocrExecuteCommand ) ;
		log.info( "OCR on file " + fileToOCR.toString() + ": " + commandSuccess ) ;
		setActivelyRunningOCR( false ) ;

		final String outputFileName = fileToOCR.getAbsolutePath().replace( ".sup", ".srt" ) ;
		final File outputFile = new File( outputFileName ) ;

		if( commandSuccess )
		{
			if( !outputFile.exists() )
			{
				log.warning( "OCR successful, but output file does not exist: " + outputFile.toString() ) ;
				commandSuccess = false ;
			}
			else if( outputFile.length() < Common.getMinimumSRTFileSize() )
			{
				log.warning( "OCR successful, but file is too small: " + outputFile.toString() ) ;
				commandSuccess = false ;
			}
		}
		if( !commandSuccess )
		{
			log.warning( "OCR failed; deleting file: " + outputFileName ) ;
			outputFile.delete() ;

			// Replace with a fake srt file
			final String fakeSRTOutputFileName = outputFileName.replace( ".srt", "." + Common.getFakeSRTSubString() + ".srt" ) ;
			File fakeSRTOutputFile = new File( fakeSRTOutputFileName ) ;
			try
			{
				fakeSRTOutputFile.createNewFile() ;
			}
			catch( Exception theException )
			{
				log.warning( "Exception creating file " + fakeSRTOutputFileName + ": " + theException.toString() ) ;
			}
		}

		return commandSuccess ;
	}

	/**
	 * Return true if at least one job is available.
	 */
	public synchronized boolean ocrJobAvailable()
	{
		synchronized( filesToOCR )
		{
			return !filesToOCR.isEmpty() ;
		}
	}

	public long getAliveCheckDuration()
	{
		return aliveCheckDuration;
	}

	public int getDefaultNumThreads()
	{
		return defaultNumThreads;
	}

	public static String[] getExtensionsToOCR()
	{
		return extensionsToOCR;
	}

	public List< File > getFilesToOCR()
	{
		return filesToOCR;
	}

	/**
	 * Returns the next filename to OCR, or null if none available.
	 * @return
	 */
	public synchronized File getFileToOCR()
	{
		File fileToOCR = null ;
		synchronized( filesToOCR )
		{
			if( !filesToOCR.isEmpty() )
			{
				fileToOCR = filesToOCR.remove( 0 ) ;
			}
		}
		return fileToOCR ;
	}

	public String getLogFileName()
	{
		return logFileName;
	}

	public String getStopFileName()
	{
		return stopFileName;
	}

	public boolean isKeepThreadRunning()
	{
		return keepThreadRunning;
	}

	/**
	 * An individual thread loop.
	 */
	@Override
	public void run()
	{
		log.info( "New thread reporting for duty." ) ;
		while( shouldKeepRunning() )
		{
			// Get a file to OCR from the queue.
			final File fileToOCR = getFileToOCR() ;
			if( null == fileToOCR )
			{
				// Queue is empty.
				// Wait for a job to appear or a stop order to be issued.
				//				log.info( "No more jobs for this thread." ) ;
				try
				{
					Thread.sleep( 100 ) ;

				}
				catch( Exception theException )
				{
					log.warning( "Exception: " + theException.toString() ) ;
				}
				continue ;
			}

			// OCR this file.
			doOCRFile( fileToOCR ) ;

			// Delete the .sup file regardless:
			// If successful, then the file should be removed so it is not re-OCRd
			// If unsuccessful, then something is probably wrong with the file and I don't
			//  want to try to re-OCR it.
			final String srtFileName = Common.replaceExtension( fileToOCR.getAbsolutePath(),  "srt" ) ;
			File srtFile = new File( srtFileName ) ;
			if( !srtFile.exists() )
			{
				final String fakeSRTFileName = Common.replaceExtension( fileToOCR.getAbsolutePath(),  "."
						+ Common.getFakeSRTSubString() + ".srt" ) ;
				log.info( "Failed to find SRT file " + srtFileName + "; creating fake srt file: " + fakeSRTFileName ) ;
				File fakeSRTFile = new File( fakeSRTFileName ) ;
				try
				{
					fakeSRTFile.createNewFile() ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception creating fake SRT file " + fakeSRTFileName + ": " + theException.toString() ) ;
				}
			}

			log.info( "Deleting OCR input file: " + fileToOCR.toString() ) ;
			if( !common.getTestMode() )
			{
				fileToOCR.delete() ;
			}
			log.info( "Completed OCR on file: " + fileToOCR.toString() ) ;
		}
		log.info( "Thread is shutdown." ) ;
	}

	public void runThreads()
	{
		runThreads( getDefaultNumThreads() ) ;
	}

	/**
	 * Run one or more threads to OCR the available .sup files.
	 * Note that the files to OCR must either be set prior to this invocation,
	 *  or the controller process must feed new file names into the queue to
	 *  process.
	 * Will keep running while the stop file is absent and the keepThreadRunning boolean
	 *  is true.
	 * @param numThreads
	 */
	public void runThreads( final int numThreads )
	{
		// Retrieve all of the drives and folders containing mkv files to find .sup files
		List< File > filesToOCR = getFilesToOCR() ;
		synchronized( filesToOCR )
		{
			log.info( "Running OCR on " + filesToOCR.size() + " file(s)" ) ;
		}

		// Create and start the threads.
		List< OCRSubtitle > ocrThreads = new ArrayList< OCRSubtitle >() ;
		for( int i = 0 ; i < numThreads ; ++i )
		{
			OCRSubtitle ocrInstance = new OCRSubtitle() ;

			// This is a bit confusing to visualize, but we need to pass along
			// the file names and extensions to OCR to each subordinate thread.
			// All threads, including this controller thread, will reference the same
			// work queue.
			ocrInstance.setFilesToOCR( getFilesToOCR() );
			ocrThreads.add( ocrInstance ) ;

			// Upon starting the thread, it will begin pulling ocr jobs from the queue and continue
			// until the queue is empty or the controller thread tells it to stop.
			ocrInstance.start() ;
		}

		// This loop is for the controller thread.
		// The below calls to stopRunningThread() are used to shutdown each individual thread.
		Thread.currentThread().setPriority( Thread.MIN_PRIORITY ) ;
		long lastAliveCheck = System.currentTimeMillis() ;
		while( shouldKeepRunning() )
		{
			try
			{
				// Sleep while we should keep running at at least one thread is busy working.
				Thread.sleep( 100 ) ;

				long timeNow = System.currentTimeMillis() ;
				if( (timeNow - lastAliveCheck) >= getAliveCheckDuration() )
				{
					// Check the status of the threads
					lastAliveCheck = timeNow ;
					int numAliveThreads = 0 ;
					int numDeadThreads = 0 ;
					int numActiveOCR = 0 ;

					for( OCRSubtitle ocrThread : ocrThreads )
					{
						if( ocrThread.isAlive() )
						{
							++numAliveThreads ;
						}
						else
						{
							++numDeadThreads ;
						}
						if( ocrThread.isActivelyRunningOCR() )
						{
							++numActiveOCR ;
						}
					}
					int queueSize = -1 ;
					synchronized( filesToOCR )
					{
						queueSize = filesToOCR.size() ;
					}
					log.info( "Alive threads: " + numAliveThreads + ", dead threads: " + numDeadThreads + ", active OCRs: "
							+ numActiveOCR + ", queue size: " + queueSize ) ;
				}
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

	public void setAliveCheckDuration( long aliveCheckDuration )
	{
		this.aliveCheckDuration = aliveCheckDuration;
	}

	public void setDefaultNumThreads(int defaultNumThreads)
	{
		this.defaultNumThreads = defaultNumThreads;
	}

	public static void setExtensionsToOCR( String[] _extensionsToOCR )
	{
		extensionsToOCR = _extensionsToOCR ;
	}

	public void setFilesToOCR( List< File > fileNamesToOCR )
	{
		this.filesToOCR = fileNamesToOCR;
	}

	public void setKeepThreadRunning( boolean keepThreadRunning )
	{
		this.keepThreadRunning = keepThreadRunning;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepThreadRunning()) ;
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
}
