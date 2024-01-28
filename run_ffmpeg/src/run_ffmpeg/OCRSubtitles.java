package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

/**
 * Execute OCR on image-based subtitle files.
 * Objects of this class use a controller/worker thread arrangement to OCR files. The controller thread maintains the pqueue
 *  of files to OCR and the worker threads retrieve one file at a time to OCR.
 * @author Dan
 */
public class OCRSubtitles extends run_ffmpegControllerThreadTemplate< OCRSubtitlesWorkerThread >
{
	/// The default number of threads to run.
	private int defaultNumThreads = 6 ;

	/// Duration, in milliseconds, between thread liveness checks.
	private long aliveCheckDuration = 5000 ;

	/// The last time the main thread checked that the worker threads were alive.
	private long lastAliveCheck = 0 ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_ocr_subtitle.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_ocr_subtitle.txt" ;

	/// The list of filenames to OCR
	/// Will be used as the job priority queue.
	/// Run OCR largest to smallest to (hopefully) minimize the time required to execute.
	private PriorityBlockingQueue< File > filesToOCR = new PriorityBlockingQueue< File >( 50, new FileSortLargeToSmall() ) ;

	/// The extensions that contain image-based subtitles
	private static String[] extensionsToOCR = { ".sup" } ;

	/**
	 * The default constructor uses the static file names.
	 */
	public OCRSubtitles()
	{
		super( logFileName, stopFileName ) ;
	}

	/**
	 * This constructor is intended for external users of this class and allows passing names of the files used
	 *  externally.
	 * @param logFileName
	 * @param stopFileName
	 */
	public OCRSubtitles( final String logFileName, final String stopFileName )
	{
		super( logFileName, stopFileName ) ;
	}

	public OCRSubtitles( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
	}

	public OCRSubtitles( Logger log,
			Common common,
			final String stopFileName,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
	}
	
	public static void main( String[] args )
	{
		OCRSubtitles ocrs = new OCRSubtitles() ;
		ocrs.Init() ;
		ocrs.Execute() ;
		System.out.println( "Process shut down." ) ;
	}

	/**
	 * Override the method called when execution begins.
	 */
	@Override
	public void Init()
	{
		setUseThreads( true ) ;
		common.setTestMode( false ) ;

		List< String > foldersToOCR = new ArrayList< String >() ;
		foldersToOCR.add( "c:\\temp\\To OCR\\Barbie (2023)" ) ;

		for( String folder : foldersToOCR )
		{
			filesToOCR.addAll( common.getFilesInDirectoryByExtension( folder, getExtensionsToOCR() ) ) ;
		}
		log.info( "Running OCR on " + filesToOCR.size() + " file(s)" ) ;
	}

	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	@Override
	protected List< OCRSubtitlesWorkerThread > buildWorkerThreads()
	{
		assert( !filesToOCR.isEmpty() ) ;
		List< OCRSubtitlesWorkerThread > threads = new ArrayList< OCRSubtitlesWorkerThread >() ;

		if( isUseThreads() )
		{
			for( int i = 0 ; i < getDefaultNumThreads() ; ++i )
			{
				OCRSubtitlesWorkerThread ocrThread = new OCRSubtitlesWorkerThread( this, log, common ) ;
				ocrThread.setName( "OCR Thread " + (i + 1) ) ;
				threads.add( ocrThread ) ;
			}
		}
		else
		{
			OCRSubtitlesWorkerThread ocrThread = new OCRSubtitlesWorkerThread( this, log, common ) ;
			ocrThread.setName( getSingleThreadedName() ) ;
			threads.add( ocrThread ) ;
		}
		return threads ;
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
	 * Return the number of active OCR actions.
	 * @return
	 */
	public int countActiveOCR()
	{
		int numActiveOCR = 0 ;
		OCRSubtitlesWorkerThread[] workerThreads = getWorkerThreads() ;
		for( OCRSubtitlesWorkerThread theThread : workerThreads )
		{
			if( theThread.isWorkInProgress() )
			{
				++numActiveOCR ;
			}
		} // for( theThread )
		return numActiveOCR ;
	}
	
	/**
	 * Execute one instance of the main loop. This method is called by the super class instance.
	 * @param numThreads
	 */
	@Override
	public void Execute_mainLoopStart()
	{
		OCRSubtitlesWorkerThread[] workerThreads = getWorkerThreads() ;
		assert( workerThreads.length > 0 ) ;

		if( !isUseThreads() )
		{
			// NOT using threads.
			// Just run the single worker for one loop.
			workerThreads[ 0 ].run() ;
			return ;
		}

		// Using threads.
		// The only thing we do here is to provide liveness checks and reporting every period.
		long timeNow = System.currentTimeMillis() ;
		if( (timeNow - lastAliveCheck) >= getAliveCheckDuration() )
		{
			// Check the status of the threads
			lastAliveCheck = timeNow ;
			int numAliveThreads = 0 ;
			int numDeadThreads = 0 ;
			int numActiveOCR = 0 ;

			// Walk through the threadMap to check for number of alive and dead threads and report out status.
			for( OCRSubtitlesWorkerThread theThread : workerThreads )
			{
				if( theThread.isAlive() )
				{
					++numAliveThreads ;
				}
				else
				{
					++numDeadThreads ;
				}

				if( theThread.isWorkInProgress() )
				{
					++numActiveOCR ;
				}
			} // for( theThread )

			int queueSize = -1 ;
			synchronized( filesToOCR )
			{
				queueSize = filesToOCR.size() ;
			}
			log.info( "Alive threads: " + numAliveThreads + ", dead threads: " + numDeadThreads + ", active OCRs: "
					+ numActiveOCR + ", queue size: " + queueSize ) ;
		} // if( need to update liveness report )
	} // Execute_mainLoopStart()

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

	public PriorityBlockingQueue< File > getFilesToOCR()
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
			try
			{
				if( !filesToOCR.isEmpty() )
				{
					fileToOCR = filesToOCR.take() ;
				}
			}
			catch( Exception theException )
			{
				log.warning( "Error in take(): " + theException ) ;
			}
		}
		return fileToOCR ;
	}

	protected OCRSubtitlesWorkerThread[] getWorkerThreads()
	{
		OCRSubtitlesWorkerThread[] workerThreads = new OCRSubtitlesWorkerThread[ 0 ] ;
		OCRSubtitlesWorkerThread[] retMe = getWorkerThreads( workerThreads ) ;
		return retMe ;
	}
	
//	/**
//	 * Return true if more work is available, and false otherwise.
//	 */
//	@Override
//	public boolean hasMoreWork()
//	{
//		return !filesToOCR.isEmpty() ;
//	}

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

	public void setAliveCheckDuration( long aliveCheckDuration )
	{
		this.aliveCheckDuration = aliveCheckDuration ;
	}

	public void setDefaultNumThreads( int defaultNumThreads )
	{
		this.defaultNumThreads = defaultNumThreads ;
	}

	public static void setExtensionsToOCR( String[] _extensionsToOCR )
	{
		extensionsToOCR = _extensionsToOCR ;
	}

	public void setFilesToOCR( PriorityBlockingQueue< File > fileNamesToOCR )
	{
		this.filesToOCR = fileNamesToOCR ;
	}
}
