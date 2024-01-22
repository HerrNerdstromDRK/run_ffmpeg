package run_ffmpeg;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

/**
 * Generic base class for controller threads to inherit basic methods.
 * ControllerThreadType is the controller thread specialization (like ProbeDirectories), and
 *  WorkerThreadType is the worker thread specialization (like ProbeDirectoriesWorkerThread).
 * @param <T>
 */
public abstract class run_ffmpegControllerThreadTemplate< WorkerThreadType extends run_ffmpegWorkerThread >
{
	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	protected transient MoviesAndShowsMongoDB masMDB = null ;
	protected transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// The structure that contains all worker threads, indexed by the drive being scanned.
	private transient Map< String, WorkerThreadType > threadMap =
			Collections.synchronizedMap( new HashMap< String, WorkerThreadType >() ) ;

	protected boolean useThreads = true ;
	protected boolean keepRunning = true ;
	protected final String singleThreadedName = "Single thread" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private String stopFileName = "C:\\Temp\\unnamed_stop_file_name.txt" ;

	public run_ffmpegControllerThreadTemplate( final String logFileName, final String stopFileName )
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		masMDB = new MoviesAndShowsMongoDB( log ) ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
		this.stopFileName = new String( stopFileName ) ;
	}

	public run_ffmpegControllerThreadTemplate( Logger log,
			Common common,
			String stopFileName,
			MoviesAndShowsMongoDB masMDB,
			MongoCollection< FFmpegProbeResult > probeInfoCollection )
	{
		assert( log != null ) ;
		assert( common != null ) ;
		assert( stopFileName != null ) ;
		assert( !stopFileName.isEmpty() ) ;
		assert( !stopFileName.isBlank() ) ;
		assert( masMDB != null ) ;
		assert( probeInfoCollection != null ) ;

		this.log = log ;
		this.common = common ;
		this.masMDB = masMDB ;
		this.probeInfoCollection = probeInfoCollection ;
		this.stopFileName = new String( stopFileName ) ;
	}

	/**
	 * Abstract method that subclasses will use to setup configuration.
	 */
	public abstract void Init() ;

	/**
	 * Execute as the controller thread.
	 * Methods will be invoked in this order, and any one of these methods can be overridden:
		Execute_start() ;
	 	Execute_beforeBuildWorkerThreads() ;
	 	// buildWorkerThreads() ;
	   	Execute_afterBuildWorkerThreads() ;
		Execute_beforeStartThreads() ;
		// threads will be start()'d
		Execute_afterStartThreads() ;
		Execute_onBeginMainLoop()
		// main loop
		Execute_onEndMainLoop() ;
	  	Execute_onStartShutdown() ;
		Execute_beforeStopThreads() ;
		// threads will be stop()'d
		Execute_afterStopThreads() ;
		Execute_beforeJoinThreads() ;
		// threads will join()'d
		Execute_afterJoinThreads() ;
		Execute_onEndShutdown() ;
		Execute_end() ;
	 */
	public void Execute()
	{
		//		if( !shouldKeepRunning() )
		//		{
		//			log.info( "Stopping process before it started because shouldKeepRunning() returned false." ) ;
		//			return ;
		//		}
		Execute_start() ;

		// Build the worker threads and add them to the internal map
		Execute_beforeBuildWorkerThreads() ;
		List< WorkerThreadType > theThreads = buildWorkerThreads() ;
		for( WorkerThreadType theThread : theThreads )
		{
			threadMap.put( theThread.getName(), theThread ) ;
		}
		Execute_afterBuildWorkerThreads() ;

		Execute_beforeStartThreads() ;
		startThreads() ;
		Execute_afterStartThreads() ;

		Execute_beforeStartMainLoop() ;

		if( !isUseThreads() )
		{
			// Single threaded.
			Execute_mainLoopStart() ;

			// The pipeline objects will likely have more than one worker "thread" even though it
			// will be single threaded. However, allow those pipelines to work synchronously.
			for( Map.Entry< String, WorkerThreadType > entrySet : threadMap.entrySet() )
			{
				WorkerThreadType workerThread = entrySet.getValue() ;
				workerThread.run() ;
			}

			Execute_mainLoopEnd() ;
		}
		else
		{
			// Using threads
			// Just wait for the threads to complete or the shutdown command to be issued.
			while( shouldKeepRunning() && atLeastOneThreadIsAlive() )
			{
				Execute_mainLoopStart() ;
				try
				{
					Thread.sleep( 100 ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error in sleep: " + theException.toString() ) ;
				}
				Execute_mainLoopEnd() ;
			}
		} // if( !isUseThreads() )
		Execute_afterEndMainLoop() ;

		Execute_onStartShutdown() ;
		log.info( "Shutting down" ) ;

		Execute_beforeStopThreads() ;
		log.info( "Stopping threads..." ) ;
		stopThreads() ;
		Execute_afterStopThreads() ;

		Execute_beforeJointThreads() ;
		log.info( "Joining threads..." ) ;
		joinThreads() ;
		Execute_afterJoinThreads() ;

		Execute_onEndShutdown() ;
		Execute_end() ;
	}

	public void Execute_mainLoopStart()
	{}

	public void Execute_mainLoopEnd()
	{}

	public void Execute_start()
	{}

	public void Execute_beforeBuildWorkerThreads()
	{}

	public void Execute_afterBuildWorkerThreads()
	{}

	public void Execute_beforeStartThreads()
	{}

	public void Execute_afterStartThreads()
	{}

	public void Execute_beforeStartMainLoop()
	{}

	public void Execute_afterEndMainLoop()
	{}

	public void Execute_onStartShutdown() 
	{}

	public void Execute_beforeStopThreads() 
	{}

	public void Execute_afterStopThreads()
	{}

	public void Execute_beforeJointThreads()
	{}

	public void Execute_afterJoinThreads() 
	{}

	public void Execute_onEndShutdown() 
	{}

	public void Execute_end()
	{}

	protected boolean atLeastOneThreadIsAlive()
	{
		if( !isUseThreads() )
		{
			return true ;
		}

		for( Map.Entry< String, WorkerThreadType > entry : threadMap.entrySet() )
		{
			WorkerThreadType workerThread = entry.getValue() ;

			if( workerThread.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}

	/**
	 * Subclasses must implement this method.
	 * Build the worker threads for this instance.
	 * Each thread will only receive the portion of the probeInfoMap related to its drive.
	 * Note that this means the drive prefixes must be mutually exclusive.
	 */
	protected abstract List< WorkerThreadType > buildWorkerThreads() ;

	public String getSingleThreadedName()
	{
		return singleThreadedName ;
	}

	protected WorkerThreadType[] getWorkerThreads( WorkerThreadType[] array )
	{
		return threadMap.values().toArray( array ) ;
	}

	public boolean isUseThreads()
	{
		return useThreads ;
	}

	protected void joinThreads()
	{
		if( isUseThreads() )
		{
			for( Map.Entry< String, WorkerThreadType > entry : threadMap.entrySet() )
			{
				final String key = entry.getKey() ;
				WorkerThreadType workerThread = entry.getValue() ;

				log.info( "Joining thread " + key ) ;
				try
				{
					workerThread.join() ;
					log.fine( "Joined thread " + key ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Error joining thread " + key + ": " + theException.toString() ) ;
				}
			}
		}
	}

	public void setUseThreads( boolean useThreads )
	{
		this.useThreads = useThreads ;
		log.info( "Threads: " + useThreads ) ;
	}

	/**
	 * Return true if both keepRunning is true and the stop execution file is absent.
	 * @return
	 */
	public boolean shouldKeepRunning()
	{
		boolean testKeepRunning = true ;
		if( !keepRunning )
		{
			testKeepRunning = false ;
		}
		else if( common.shouldStopExecution( stopFileName ) )
		{
			testKeepRunning = false ;
		}
		return testKeepRunning ;
	}

	protected void startThreads()
	{
		if( isUseThreads() )
		{
			log.info( "Starting threads." ) ;
			for( Map.Entry< String, WorkerThreadType > entry : threadMap.entrySet() )
			{
				final String key = entry.getKey() ;
				WorkerThreadType workerThread = entry.getValue() ;

				log.info( "Starting thread " + key ) ;
				workerThread.start() ;
			}
		}
	}

	public void stopRunning()
	{
		keepRunning = false ;
	}

	protected void stopThreads()
	{
		stopRunning() ;
	}
}
