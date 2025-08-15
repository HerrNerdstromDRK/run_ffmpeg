package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

public class Subtitles_Transcribe extends run_ffmpegControllerThreadTemplate< Subtitles_TranscribeWorkerThread >
{
	/// The default number of worker threads to run. This is different than the number of thread each transcription will use.
	private int defaultNumThreads = 1 ;

	/// Duration, in milliseconds, between thread liveness checks.
	private long aliveCheckDuration = 5000 ;

	/// The last time the main thread checked that the worker threads were alive.
	private long lastAliveCheck = 0 ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_subtitles_transcribe.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_subtitles_transcribe.txt" ;

	/// The list of filenames to transcribe.
	/// Will be used as the job priority queue.
	/// Run transcription largest to smallest to (hopefully) minimize the time required to execute (if using more than one thread).
	private PriorityBlockingQueue< File > filesToTranscribe = new PriorityBlockingQueue< File >( 50, new FileSortLargeToSmall() ) ;

	/// The extensions that contain audio to transcribe.
	private static String[] extensionsToTranscribe = { ".wav" } ;

	/**
	 * The default constructor uses the static file names.
	 */
	public Subtitles_Transcribe()
	{
		super( logFileName, stopFileName ) ;
	}

	/**
	 * This constructor is intended for external users of this class and allows passing names of the files used
	 *  externally.
	 * @param logFileName
	 * @param stopFileName
	 */
	public Subtitles_Transcribe( final String logFileName, final String stopFileName )
	{
		super( logFileName, stopFileName ) ;
	}

	public Subtitles_Transcribe( Logger log,
			Common common,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpeg_ProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
	}

	public Subtitles_Transcribe( Logger log,
			Common common,
			final String stopFileName,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpeg_ProbeResult > probeInfoCollection )
	{
		super( log, common, stopFileName, masMDB, probeInfoCollection ) ;
	}
	
	public static void main( String[] args )
	{
		Subtitles_Transcribe subtitles_Transcribe = new Subtitles_Transcribe() ;
		subtitles_Transcribe.Init() ;
		subtitles_Transcribe.Execute() ;
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

		List< String > foldersToTranscribe = new ArrayList< String >() ;
		foldersToTranscribe.add( "c:\\temp\\To OCR\\Barbie (2023)" ) ;

		for( String folder : foldersToTranscribe )
		{
			filesToTranscribe.addAll( common.getFilesInDirectoryByExtension( folder, getExtensionsToTranscribe() ) ) ;
		}
		log.info( "Running transcribe on " + filesToTranscribe.size() + " file(s)" ) ;
	}

	/**
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	@Override
	protected List< Subtitles_TranscribeWorkerThread > buildWorkerThreads()
	{
		assert( !filesToTranscribe.isEmpty() ) ;
		List< Subtitles_TranscribeWorkerThread > threads = new ArrayList< Subtitles_TranscribeWorkerThread >() ;

		if( isUseThreads() )
		{
			for( int i = 0 ; i < getDefaultNumThreads() ; ++i )
			{
				Subtitles_TranscribeWorkerThread transcribeThread = new Subtitles_TranscribeWorkerThread( this, log, common ) ;
				transcribeThread.setName( "Transcribe Thread " + (i + 1) ) ;
				threads.add( transcribeThread ) ;
			}
		}
		else
		{
			Subtitles_TranscribeWorkerThread transcribeThread = new Subtitles_TranscribeWorkerThread( this, log, common ) ;
			transcribeThread.setName( getSingleThreadedName() ) ;
			threads.add( transcribeThread ) ;
		}
		return threads ;
	}

	public void addFileNameToTranscribe( final String newFileNameToTranscribe )
	{
		addFileToTranscribe( new File( newFileNameToTranscribe ) ) ;
	}

	public void addFileNamesToTranscribe( final List< String > newFileNamesToTranscribe )
	{
		for( String fileName : newFileNamesToTranscribe )
		{
			addFileToTranscribe( new File( fileName ) ) ;
		}
	}

	public void addFilesToTranscribe( final List< File > newFilesToTranscribe )
	{
		synchronized( filesToTranscribe )
		{
			filesToTranscribe.addAll( newFilesToTranscribe ) ;
		}
	}

	public void addFileToTranscribe( File newFileToTranscribe )
	{
		synchronized( filesToTranscribe )
		{
			filesToTranscribe.add( newFileToTranscribe ) ;
		}
	}

	public void addFoldersToTranscribe( List< String > folderPaths )
	{
		for( String theFolder : folderPaths )
		{
			final List< File > filesToTranscribe = common.getFilesInDirectoryByExtension( theFolder, getExtensionsToTranscribe() ) ;
			addFilesToTranscribe( filesToTranscribe ) ;
		}
	}

	/**
	 * Return the number of active transcribe actions.
	 * @return
	 */
	public int countActiveTranscriptions()
	{
		int numActiveTranscriptions = 0 ;
		Subtitles_TranscribeWorkerThread[] workerThreads = getWorkerThreads() ;
		for( Subtitles_TranscribeWorkerThread theThread : workerThreads )
		{
			if( theThread.isWorkInProgress() )
			{
				++numActiveTranscriptions ;
			}
		} // for( theThread )
		return numActiveTranscriptions ;
	}
	
	/**
	 * Execute one instance of the main loop. This method is called by the super class instance.
	 * @param numThreads
	 */
	@Override
	public void Execute_mainLoopStart()
	{
		Subtitles_TranscribeWorkerThread[] workerThreads = getWorkerThreads() ;
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
			int numActiveTranscriptions = 0 ;

			// Walk through the threadMap to check for number of alive and dead threads and report out status.
			for( Subtitles_TranscribeWorkerThread theThread : workerThreads )
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
					++numActiveTranscriptions ;
				}
			} // for( theThread )

			int queueSize = -1 ;
			synchronized( filesToTranscribe )
			{
				queueSize = filesToTranscribe.size() ;
			}
			log.info( "Alive threads: " + numAliveThreads + ", dead threads: " + numDeadThreads + ", active transcriptions: "
					+ numActiveTranscriptions + ", queue size: " + queueSize ) ;
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

	public static String[] getExtensionsToTranscribe()
	{
		return extensionsToTranscribe ;
	}

	public PriorityBlockingQueue< File > getFilesToTranscribe()
	{
		return filesToTranscribe ;
	}

	/**
	 * Returns the next filename to transcribe, or null if none available.
	 * @return
	 */
	public synchronized File getFileToTranscribe()
	{
		File fileToTranscribe = null ;
		synchronized( filesToTranscribe )
		{
			try
			{
				if( !filesToTranscribe.isEmpty() )
				{
					fileToTranscribe = filesToTranscribe.take() ;
				}
			}
			catch( Exception theException )
			{
				log.warning( "Error in take(): " + theException ) ;
			}
		}
		return fileToTranscribe ;
	}

	protected Subtitles_TranscribeWorkerThread[] getWorkerThreads()
	{
		Subtitles_TranscribeWorkerThread[] workerThreads = new Subtitles_TranscribeWorkerThread[ 0 ] ;
		Subtitles_TranscribeWorkerThread[] retMe = getWorkerThreads( workerThreads ) ;
		return retMe ;
	}

	/**
	 * Return true if at least one job is available.
	 */
	public synchronized boolean transcribeJobAvailable()
	{
		synchronized( filesToTranscribe )
		{
			return !filesToTranscribe.isEmpty() ;
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

	public static void setExtensionsToTranscribe( String[] _extensionsToTranscribe )
	{
		extensionsToTranscribe = _extensionsToTranscribe ;
	}

	public void setFilesToTranscribe( PriorityBlockingQueue< File > fileNamesToTranscribe )
	{
		this.filesToTranscribe = fileNamesToTranscribe ;
	}
}
