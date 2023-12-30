package run_ffmpeg;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList ;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;
import java.io.File ;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * The purpose of this class is to add subtitles into existing mp4 files.
 * If the subtitles are standard, then remux the existing mp4 with the addition of the
 *  subtitles.
 * If the subtitles are forced, such as for foreign movies (Ip Man) or have partial foreign language
 *  subtitles, such as many episode of Game of Thrones, then retranscode that movie/tv show with
 *  forced subtitles, meaning they are burned into the mp4 file. Also add any missed subtitles to
 *  those files.
 * Finally, the class design includes a separate provision to move the mp4 files from local storage
 *  to permanent storage (D:\Temp->\\yoda\\MP4\\...) as a separate thread. This is done to prevent
 *  resource competition amongst executing remux/retranscode threads.
 */
public class RemuxWithSubtitles extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_remux_with_subtitles.txt" ;

	/// Temporary directory where MP4 files are built with ffmpeg, to moved from there
	/// to the final directory later.
	private final String mp4OutputDirectory = "D:\\Temp" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_remux_threads.txt" ;

	/// Set to true to use threads, false otherwise.
	private boolean useThreads = false ;

	private Map< String, RemuxWithSubtitles > workerThreads = new TreeMap< String, RemuxWithSubtitles >() ;

	/// The time, in ms, to wait for a file to sync before attempting to move it.
	private long fileSyncSleepTime = 500 ;
	private final String transcodeWorkerThreadName = "Transcode" ;

	/// Handles to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	/// The folders containing movies/shows to remux/retranscode/move
	private List< TranscodeFile > moviesAndShowsToRemux = new ArrayList< TranscodeFile >() ;
	private List< TranscodeFile > moviesAndShowsToRetranscode = new ArrayList< TranscodeFile >() ;
	private List< TranscodeFile > moviesAndShowsToMove = new ArrayList< TranscodeFile >() ;
	private boolean keepRunning = true ;

	private enum RemuxOrTranscodeStatusType {
		NEED_NOTHING,
		NEED_REMUX,
		NEED_RETRANSCODE
	}

	public RemuxWithSubtitles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB( log ) ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}

	public RemuxWithSubtitles( RemuxWithSubtitles rhs )
	{
		rhs.log = log ;
		rhs.common = common ;
		// logFileName is final
		// mp4OutputDirectory will not change
		// stopFileName is final
		rhs.useThreads = isUseThreads() ;
		// workerThreads will be default/empty
		rhs.fileSyncSleepTime = getFileSyncSleepTime() ;
		// transcodeWorkerThreadName will not be changed
		rhs.masMDB = masMDB ;
		rhs.movieAndShowInfoCollection = movieAndShowInfoCollection ;
		rhs.probeInfoCollection = probeInfoCollection ;
		// moviesAndShowsToRemux/Retranscode/Move will be set dynamically
		// keepRunning defaults to true		
	}

	public static void main( String[] args )
	{
		RemuxWithSubtitles rws = new RemuxWithSubtitles() ;
		rws.execute() ;
		System.out.println( "main> Shutdown." ) ;
	}

	/**
	 * Non-static entry point for the core algorithm.
	 */
	public void execute()
	{
		setUseThreads( true ) ;
		common.setTestMode( false ) ;

		// Get all of the MP4 drives
		List< String > mp4DrivesWithoutFolders = common.getAllMP4Drives() ;
		// If I want to specify a single folder to remux/retranscode, then add it here.
		// This only works on MP4 files and folders, and the folder name must match the database entry
		//  (\\yoda\MP4 instead of "Q:\")
		if( common.getTestMode() )
		{
			mp4DrivesWithoutFolders = new ArrayList< String >() ;
			mp4DrivesWithoutFolders.add( "\\\\yoda\\MP4" ) ;
		}

		// Add "Movies" and "TV Shows" to each of MP4 drives
		List< String > mp4Drives = common.addMoviesAndTVShowFoldersToEachDrive( mp4DrivesWithoutFolders ) ;
		log.info( getName() + " Will remux/retranscode the following top level folders: " + mp4Drives.toString() ) ;

		// Find the lowest level folders in each directory tree. This will be used for searching for MovieAndShowInfos.
		// The list of folders will be unique -- no duplicates.
		List< String > mp4MovieAndShowInfoFolders = new ArrayList< String >() ;
		for( String mp4DriveFolder : mp4Drives )
		{
			log.info(  getName() +  "Finding all subdirectories in " + mp4DriveFolder + "; this may take a minute." ) ;
			mp4MovieAndShowInfoFolders.addAll( common.findLowestLevelDirectories( mp4DriveFolder ) ) ;
		}

		log.info(  getName() + " Remuxing/retranscoding " + mp4MovieAndShowInfoFolders.size() + " movies/tv shows "
				+ (useThreads ? "with" : "without" ) + " threads" ) ;

		// Download the entire MovieAndShowInfo and ProbeInfo collections locally as a means to optimize.
		Map< String, MovieAndShowInfo > movieAndShowInfoMap = getAllMovieAndShowInfosKeyedByMP4LongPath() ;
		Map< String, FFmpegProbeResult > probeInfoMap = getAllProbeInfoResults() ;

		log.info(  getName() + " Building remux and transcode list; this could take a minute." ) ;
		// Iterate through each MovieAndShowInfo and add the files to remux and transcode
		for( Map.Entry< String, MovieAndShowInfo > masEntry : movieAndShowInfoMap.entrySet() )
		{
			MovieAndShowInfo theMovieAndShowInfo = masEntry.getValue() ;
			buildRemuxAndTranscodeList( theMovieAndShowInfo, probeInfoMap, moviesAndShowsToRemux, moviesAndShowsToRetranscode ) ;
		}

		log.info(  getName() + " remuxing " + moviesAndShowsToRemux.size() + " file(s) and retranscoding " + moviesAndShowsToRetranscode.size() + " file(s)" ) ;
		log.fine(  getName() + " Remuxing: " + moviesAndShowsToRemux.toString() ) ;
		log.fine(  getName() + " Retranscoding: " + moviesAndShowsToRetranscode.toString() ) ;

		// Build a worker thread for each MP4 drive and one as a dedicated thread to transcode.
		workerThreads = buildWorkerThreads( moviesAndShowsToRemux, moviesAndShowsToRetranscode ) ;

		// Since each worker thread will need to submit move requests to the relevant mp4 worker thread
		//  (inter-thread communication), pass along the Map of worker threads to each worker.
		for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
		{
			RemuxWithSubtitles rwsWorker = entry.getValue() ;
			rwsWorker.setWorkerThreads( workerThreads ) ;
		}

		// If using threads, then start the threads.
		if( isUseThreads() )
		{
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				log.info( getName() + " Starting thread: " + rwsWorker.getName() ) ;
				rwsWorker.start() ;
			}
		}

		// Main loop.
		// If not using threads, execute the workers sequentially until they are all complete, then shutdown.
		// If using threads, then sleep until all threads finish with their work or we receive notification
		//  to shutdown.
		while( shouldKeepRunning() && !isAllWorkDone() )
		{
			if( !isUseThreads() )
			{
				// Not using threads.
				// Iterate through the workers and execute each one in turn.
				for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
				{
					if( !shouldKeepRunning() )
					{
						// Flag set to shutdown.
						break ;
					}
					// Note: run() will check shouldKeepRunning() after each movie or show.
					RemuxWithSubtitles rwsWorker = entry.getValue() ;
					//						log.info( getName() + " Running new worker..." ) ;

					// This avoids the situation where all files are remuxed and transcoded,
					//  which creates a backlog of move functions needed, and probably overruns the
					//  space available on the mp4Output drive.
					// Running one loop at a time ensures that the output directory stays mostly clear.
					rwsWorker.runOneLoop() ;
				} // for( rwsWorker )
			} // if( !isUseThreads() )
			else // using threads
			{
				// Only sleep if threads are running -- this will prevent a spin lock that consumes
				// the CPU unnecessarily.
				try
				{
					Thread.sleep( 100 ) ;
				}
				catch( Exception theException )
				{
					log.warning(  getName() + " Exception: " + theException.toString() ) ;
				}
			} // else -- using threads
		} // while( shouldKeepRunning() && !isAllWorkDone() )
		log.info( getName() + " Shutting down..." ) ;

		// The MoveFiles instance automatically starts two worker threads to move MKV and MP4 files.
		// They are not used here, but I still need to shut them down cleanly.
		log.info( getName() + " Shutting down MoveFiles threads." ) ;

		// Stop threads
		if( isUseThreads() )
		{
			log.info( getName() + " Stopping threads..." ) ;	
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				log.info( getName() + " Stopping thread: " + rwsWorker.getName() ) ;
				rwsWorker.setKeepRunning( false ) ;
			}
		}

		// Join threads
		if( isUseThreads() )
		{
			log.info( getName() + " Joining threads..." ) ;	
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				try
				{
					log.info( getName() + " Joining thread: " + rwsWorker.getName() ) ;
					rwsWorker.join() ;
					log.info( getName() + " Joined: " + rwsWorker.getName() ) ;
				}
				catch( Exception theException )
				{
					log.warning(  getName() + " Exception: " + theException.toString() ) ;
				}
			}
		}
	}

	/**
	 * Method to remux/retranscode everything on a single drive. This may be run as an individual thread, or sequentially
	 *  if single threaded.
	 */
	@Override
	public void run()
	{

		// Keep running while the termination file is absent and while any thread has work remaining.
		while( shouldKeepRunning() && !isAllWorkDone() )
		{
			// Execute one loop of work (1 remux, 1 retranscode, and all local moves)
			boolean didSomeWork = runOneLoop() ;

			if( !didSomeWork && isUseThreads() )
			{
				// Nothing was remuxed, transcoded, or moved.
				// This might be ok if this thread is responsible for moving items since other threads might
				// still be remuxing or retranscoding -- work may show up later.
				// Avoid a spin lock by sleeping here.
				try
				{
					Thread.sleep( 100 ) ;
				}
				catch( Exception theException )
				{
					log.warning( getName() + " Error sleeping: " + theException.toString() ) ;
				}
			}
		} // while()
		log.info( getName() + " Thread shutting down; shouldKeepRunning(): " + shouldKeepRunning() + ", isAllWorkDone(): " + isAllWorkDone()
		+ "; isUseThreads: " + isUseThreads() ) ;
	} 

	/**
	 * Run one iteration of the main processing loop. This is a separate method to enable shutting down each thread with
	 *  some granularity by having each check for work after each remux/retranscode/move.
	 * @return
	 */
	private boolean runOneLoop()
	{
		// In order to prevent backloading all of the remux actions across the threads,
		// have each thread alternate between remux and retranscode.
		boolean didSomeWork = false ;
		if( !moviesAndShowsToRemuxIsEmpty() && shouldKeepRunning() )
		{
			// Handle remux
			TranscodeFile theFileToRemux = getMovieOrShowToRemux() ;
			if( null == theFileToRemux )
			{
				// All's fair in multi-threading.
				log.warning(  getName() + " Found null file to remux" ) ;
			}
			else
			{
				remuxOrRetranscodeFile( theFileToRemux, true ) ;
				didSomeWork = true ;
			}
		} // if( remux )

		if( !moviesAndShowsToRetranscodeIsEmpty() && shouldKeepRunning() )
		{
			TranscodeFile theFileToRetranscode = getMovieOrShowToRetranscode() ;
			if( null == theFileToRetranscode )
			{
				// All's fair in multi-threading.
				log.warning(  getName() + " Found null file to retranscode" ) ;
			}
			else
			{
				remuxOrRetranscodeFile( theFileToRetranscode, false ) ;
				didSomeWork = true ;
			}
		} // if( transcode )

		// Cheating here a bit -- favoring moving files to keep the output directory clear.
		// Clear the move queue before returning.
		while( !moviesAndShowsToMoveIsEmpty() && shouldKeepRunning() )
		{
			TranscodeFile theFileToMove = getMovieOrShowToMove() ;
			if( null == theFileToMove )
			{
				// All's fair in multi-threading.
				log.warning(  getName() + " Found null file to move" ) ;
			}
			else
			{
				log.info( getName() + " Moving: " + theFileToMove.getMP4OutputFileNameWithPath()
				+ "->" + theFileToMove.getMP4FinalFileNameWithPath() ) ;
				if( !common.getTestMode() )
				{
					MoveFiles.moveFile( theFileToMove.getMP4OutputFileNameWithPath(), theFileToMove.getMP4FinalFileNameWithPath(), log, common ) ;
				}
				didSomeWork = true ;
			}
		} // if( transcode )

		if( !shouldKeepRunning() )
		{
			log.info( getName() + " Shutting down this thread..." ) ;
		}
		return didSomeWork ;
	}

	/**
	 * Add each TV Show and Movie mkv that needs to be remuxed or re-transcoded to the
	 *  either of the provided files list.
	 * @param mp4DriveWithFolder
	 * @param filesToRemux
	 * @param filesToTranscode
	 */
	protected void buildRemuxAndTranscodeList( //final String mp4DriveWithFolder,
			MovieAndShowInfo theMovieAndShowInfo,
			Map< String, FFmpegProbeResult > probeInfoMap,
			List< TranscodeFile > filesToRemux,
			List< TranscodeFile > filesToTranscode )
	{
//		log.fine( getName() + " Processing MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
		if( theMovieAndShowInfo.getMKVLongPath().contains( common.getMissingMovieMKVPath() )
				|| theMovieAndShowInfo.getMKVLongPath().contains( common.getMissingTVShowMKVPath() ) )
		{
			log.fine( getName() + " Skipping movie/show with missing path: " + theMovieAndShowInfo.toString() ) ;
			return ;
		}

		if( theMovieAndShowInfo.getMKVLongPath().contains( "MKV_Archive4" ) )
		{
			// Still having a problem with MKV_4; ignore it for now.
			//				continue ;
			return ;
		}
		// theMovieAndShowInfo should have information about the movie or show along with
		// information about the constituent files (mkv and mp4) for it.

		// Retrieve an iterator to the correlated files for this movie or show
		for( CorrelatedFile theCorrelatedFile : theMovieAndShowInfo.getCorrelatedFilesList() )
		{
			if( theCorrelatedFile.isMissingFile() )
			{
				// No sense in continuing if the mp4 file is missing:
				//  if missing the mp4 file, then it needs to be transcoded, not retranscoded.
				//  if missing the mkv file, then it is also missing any SRT files, so we are not able to remux either.
				log.fine( getName() + " Missing file for correlated file: " + theCorrelatedFile.toString() ) ;
				continue ;
			}

			// Retrieve the probe information for the correlated file.
			final String mkvFileNameWithPath = common.addPathSeparatorIfNecessary( theMovieAndShowInfo.getMKVLongPath() )
					+ theCorrelatedFile.getFileName()  + ".mkv" ;	
			//			Bson findProbeInfoFilter = Filters.eq( "fileNameWithPath", mkvFileNameWithPath ) ;
			//			FFmpegProbeResult mkvProbeResult = probeInfoCollection.find( findProbeInfoFilter ).first() ;
			FFmpegProbeResult mkvProbeResult = probeInfoMap.get( mkvFileNameWithPath ) ;
			if( null == mkvProbeResult )
			{
				log.warning(  getName() + " Unable to find probe info for correlated file: " + mkvFileNameWithPath ) ;
				continue ;
			}

			TranscodeFile fileToRemuxOrTranscode = buildTranscodeFile( theMovieAndShowInfo, theCorrelatedFile, mkvProbeResult ) ;
			if( null == fileToRemuxOrTranscode )
			{
				// Error reading this MovieAndShowInfo
				log.warning(  getName() + " Error in buildTranscodeFile for MovieAndShowInfo: " + theMovieAndShowInfo.toString() + ", skipping" ) ;
				continue ;
			}

			// At this point, the data stored in the database meets the file system.
			final RemuxOrTranscodeStatusType needRemuxOrTranscodeStatus = needRemuxOrTranscode( fileToRemuxOrTranscode,
					fileToRemuxOrTranscode.getMP4FinalFile() ) ;
			if( RemuxOrTranscodeStatusType.NEED_NOTHING == needRemuxOrTranscodeStatus )
			{
				// Nothing to do.
			}
			else if( RemuxOrTranscodeStatusType.NEED_REMUX == needRemuxOrTranscodeStatus )
			{
				// Needs remux.
				filesToRemux.add( fileToRemuxOrTranscode );
			}
			else if( RemuxOrTranscodeStatusType.NEED_RETRANSCODE == needRemuxOrTranscodeStatus )
			{
				// Needs transcode.
				filesToTranscode.add( fileToRemuxOrTranscode ) ;
			}
			else
			{
				log.warning(  getName() + " Invalid return of " + needRemuxOrTranscodeStatus + " status from needRemuxOrTranscode "
						+ " for file: " + fileToRemuxOrTranscode.toString() ) ;
			}
		} // for( correlatedFile )
		//		} // while( movieAndShowInfoIterator.hasNext() )
	} // buildRemuxAndTranscodeList()

	/**
	 * Build a TranscodeFile based on the data provided for the given file.
	 * @param theMovieAndShowInfo
	 * @param theCorrelatedFile
	 * @param mkvProbeResult Could be null.
	 * @return
	 */
	protected TranscodeFile buildTranscodeFile( MovieAndShowInfo theMovieAndShowInfo,
			CorrelatedFile theCorrelatedFile,
			FFmpegProbeResult mkvProbeResult )
	{
		//		log.info( getName() + " Processing movie or show: " + theMovieAndShowInfo.getMovieOrShowName() + ", file: " + theCorrelatedFile.toString() ) ;

		// First determine if the both mp4 and mkv files exist.
		if( theCorrelatedFile.isMissingFile() )
		{
			log.warning(  getName() + " Missing file in correlated file: " + theCorrelatedFile ) ;
			return null ;
		}
		if( (theCorrelatedFile.getNumberOfMKVFiles() != 1) ||
				(theCorrelatedFile.getNumberOfMP4Files() != 1) )
		{
			log.warning(  getName() + " More than one MKV or MP4 files in correlated file: " + theCorrelatedFile.toString() ) ;
			return null ;
		}
		// Post condition: No missing files, and only one mp4 and one mkv file.

		final String mkvFileName = theMovieAndShowInfo.getMKVLongPath() + "\\" + theCorrelatedFile.getFileName() + ".mkv" ;
		//		final String mp4FileName = theMovieAndShowInfo.getMP4LongPath() + "\\" + theCorrelatedFile.getFileName() + ".mp4" ;
		final File mkvFile = new File( mkvFileName ) ;
		//		final File oldMP4File = new File( mp4FileName ) ;

		// For now, use this criteria to determine if this mp4 should be remuxed (or transcoded):
		//  If the mkv file or any of its subtitle files are newer than the mp4, then remux/transcode

		// Use a TranscodeFile instance to build the list of SRT files.
		final String mkvFinalDirectory = theMovieAndShowInfo.getMKVLongPath() ;
		final String mp4FinalDirectory = theMovieAndShowInfo.getMP4LongPath() ;

		TranscodeFile theTranscodeFile = new TranscodeFile(
				mkvFile,
				mkvFinalDirectory,
				getMP4OutputDirectory(),
				mp4FinalDirectory,
				log ) ;

		// Some remuxes will be on mp4 files with missing mkv files
		// (Cannot transcode on a missing MKV file.)
		// Be sure to check if we are referencing a missing mkv file.
		if( mkvProbeResult != null )
		{
			// Add the FFmpegProbeResult to the TranscodeFile...this will ensure all streams inside the MKV are accounted for
			// in the transcode parameters.
			theTranscodeFile.processFFmpegProbeResult( mkvProbeResult ) ;
		}

		return theTranscodeFile ;
	}

	/**
	 * Build the worker threads to conduct the remux/retranscodes.
	 * The thread map will be keyed by the MP4 drive in question.
	 * @param filesToRemux
	 * @param filesToTranscode
	 * @return
	 */
	protected Map< String, RemuxWithSubtitles > buildWorkerThreads( List< TranscodeFile > filesToRemux,
			List< TranscodeFile > filesToTranscode )
	{
		Map< String, RemuxWithSubtitles > threadMap = new TreeMap< String, RemuxWithSubtitles >() ;
		if( !isUseThreads() )
		{
			threadMap.put( getName(), this ) ;
			return threadMap ;
		}

		log.info( getName() + " Creating worker threads, one per MP4 drive." ) ;

		// Create a new RWS for each MP4 drive.
		List< String > mp4Drives = common.getAllMP4Drives() ;
		for( String mp4Drive : mp4Drives )
		{
			// Build a new RWS object to handle this mp4 drive.
			RemuxWithSubtitles rwsWorker = new RemuxWithSubtitles( this ) ;
			rwsWorker.setName( mp4Drive ) ;

			// Populate the RWS object with all remux files for that drive.
			int numRemuxEntries = 0 ;
			for( TranscodeFile theRemuxFile : filesToRemux )
			{
				final String mp4Path = theRemuxFile.getMP4FinalFileNameWithPath() ;
				if( mp4Path.contains( mp4Drive ) )
				{
					rwsWorker.addMovieOrShowToRemux( theRemuxFile ) ;
					++numRemuxEntries ;
				}
			}

			// Do NOT provide the transcode files to this worker -- one thread (created below) is responsible
			// to transcode all files.
			log.info( getName() + " Worker thread for " + mp4Drive + " contains " + numRemuxEntries + " items to remux; name: " +
					rwsWorker.getName() ) ;

			// Finally add the worker to the threadMap.
			threadMap.put( mp4Drive, rwsWorker ) ;
		}

		// Add the transcode worker thread.
		RemuxWithSubtitles transcodeWorkerThread = new RemuxWithSubtitles() ;
		transcodeWorkerThread.setName( "Transcode" ) ;
		transcodeWorkerThread.addMovieOrShowToRetranscode( filesToTranscode ) ;
		threadMap.put( getTranscodeWorkerThreadName(), transcodeWorkerThread ) ;

		log.info( getName() + " Created transcode worker thread with " + filesToTranscode.size() + " file(s) to transcode" ) ;

		String workerThreadMapOutput = "threadMap: " ;
		for( Map.Entry< String, RemuxWithSubtitles > entry : threadMap.entrySet() )
		{
			workerThreadMapOutput += entry.getKey() + "," ;
		}
		log.info( getName() + " " + workerThreadMapOutput ) ;

		return threadMap ;
	}

	/**
	 * Queue up a request to move a file.
	 * @param sourceFileNameWithPath
	 * @param destinationFileNameWithPath
	 */
	protected void moveFile( final TranscodeFile movieOrShowToMove )
	{
		assert( movieOrShowToMove != null ) ;

		// 26-Dec-2023: Having a problem moving files -- quite a few are generating an exception that
		//  the file is in use by another process.
		// Although it violated the Rule of Time, I currently see no better option than to insert
		//  an arbitrary sleep to let the file system catch up.

		// Mitigate this issue by attempting to open the file, with delays in between, up to a certain
		// number of times.
		int attemptNumber = 0 ;
		boolean openedSuccessfully = false ;
		File testFile = movieOrShowToMove.getMP4OutputFile() ;

		for( ; (attemptNumber <= 10) && !openedSuccessfully ; ++attemptNumber )
		{
			try
			{
				// Try to open a read stream for the file. A locked file should generate an exception.
				if( !common.getTestMode() )
				{
					FileReader fr = new FileReader( testFile ) ;
					fr.close() ;
				}

				// If the code reaches this point then it should indicate that the file opened successfully.
				openedSuccessfully = true ;
			}
			catch( Exception theException )
			{
				// Failed to open a reading stream -- the file is still unavailable
				log.warning( getName() + "Exception while reading " + testFile.getAbsolutePath() + " on attempt number " + attemptNumber ) ;
			}

			if( !openedSuccessfully )
			{
				// Failed to open the file for this attempt
				try
				{
					// Provide some more time for the file system to sync
					log.info( getName() + " Sleeping " + getFileSyncSleepTime() + " milliseconds before moveFile()"
							+ " for file " + testFile.getAbsolutePath() ) ;
					if( !common.getTestMode() )
					{
						Thread.sleep( getFileSyncSleepTime() ) ;
					}
				}
				catch( Exception theException )
				{
					log.warning(  getName() + " Error when sleeping for a file closure: " + theException.toString() ) ;
				}
			}
		} // for()
		if( openedSuccessfully )
		{
			log.info( getName() + " Successfully opened " + testFile.getAbsolutePath() + " after " + attemptNumber + " attempt(s)" ) ;
		}
		else
		{
			log.warning( getName() + "Unable to sync file " + testFile.getAbsolutePath() + " after " + attemptNumber + " attempt(s)" ) ;
		}

		// For now, continue with an attempt to move the file.
		// Get the mp4 thread responsible to move this file.
		RemuxWithSubtitles mp4Thread = getMP4Thread( movieOrShowToMove.getMP4FinalFileNameWithPath() ) ;
		if( mp4Thread != null )
		{
			log.fine( getName() + " Found mp4 thread (" + mp4Thread.getName() + ") adding move job for file " + testFile.getAbsoluteFile()
			+ "->" + movieOrShowToMove.getMP4FinalFile().getAbsolutePath() ) ;
			mp4Thread.addMovieOrShowToMove( movieOrShowToMove ) ;
		}
	}

	/**
	 * Determine if the file needs to be remuxed or retranscoded, or neither.
	 * @param theTranscodeFile
	 * @return:
	 * 	0 if this file needs neither a remux or retranscode.
	 *  1 if this file needs a remux.
	 *  2 if this file needs a retranscode.
	 */
	protected RemuxOrTranscodeStatusType needRemuxOrTranscode( final TranscodeFile theTranscodeFile, final File oldMP4File )
	{
		RemuxOrTranscodeStatusType retMe = RemuxOrTranscodeStatusType.NEED_NOTHING ;

		for( Iterator< File > srtFileIterator = theTranscodeFile.getRealSRTFileListIterator() ; srtFileIterator.hasNext() ; )
		{
			final File theSRTFile = srtFileIterator.next() ;
			final String theSRTFileName = theSRTFile.getName() ;

			if( theSRTFile.lastModified() <= oldMP4File.lastModified() )
			{
				// The SRT file is older than the mp4 file.
				// Nothing to do here.
				continue ;
			}
			// Post-Condition: The SRT file is newer than the mp4 file, meaning the subtitle has been updated.
			// Post-Condition: This MKV file needs to be remuxed or re-transcoded.
			if( theSRTFileName.contains( TranscodeCommon.getForcedSubTitleFileNameContains() ) )
			{
				// This is a forced subtitle srt file.
				// Need to re-transcode.
				retMe = RemuxOrTranscodeStatusType.NEED_RETRANSCODE ;

				// Once we find the need to retranscode, stop there. Any further looking could find a non-forced subtitle
				// and switch the status to NEED_REMUX, which would miss the need.
				break ;
			}
			else
			{
				// Not a forced subtitle srt file, but the srt file is still newer than the mp4 file.
				retMe = RemuxOrTranscodeStatusType.NEED_REMUX ;
			}
		} // for( iterator )

		return retMe ;
	}

	protected boolean remuxFile( MovieAndShowInfo theMovieAndShowInfo,
			TranscodeFile fileToTranscode )
	{
		TranscodeCommon tCommon = new TranscodeCommon(
				log,
				common,
				fileToTranscode.getMKVInputDirectory(),
				fileToTranscode.getMKVFinalDirectory(),
				fileToTranscode.getMP4OutputDirectory(),
				fileToTranscode.getMP4FinalDirectory() ) ;

		// Verify that the remux input mp4 file exists.
		if( !fileToTranscode.getMP4FinalFile().exists() )
		{
			log.warning (getName() + " Unable to find mp4FinalFile: " + fileToTranscode.getMP4FinalFile().getAbsolutePath() ) ;
			return false ;
		}

		boolean remuxSucceeded = tCommon.remuxFile( fileToTranscode ) ;
		return remuxSucceeded ;
	}

	/**
	 * Remux or retranscode a file.
	 * @param folderToRemux
	 * @param doRemux true if remux, false if transcode
	 */
	private void remuxOrRetranscodeFile( final TranscodeFile fileToRemuxOrRetranscode, boolean doRemux )
	{
		// Each folder should be of the form "\\\\yoda\\MP4\\Movies\\Movie Name (yyyy)" (or TV Show\\Season xx)
		// Use that path to search the database by mp4LongPath
		log.info( getName() + " Running find on file (" + (doRemux ? "remux" : "retranscode") +"): " + fileToRemuxOrRetranscode ) ;

		final String mp4LongPath = fileToRemuxOrRetranscode.getMP4FinalDirectory() ;
		Bson findFileFilter = Filters.eq( "mp4LongPath", mp4LongPath ) ;
		FindIterable< MovieAndShowInfo > movieAndShowInfoFindResult = movieAndShowInfoCollection.find( findFileFilter ) ;

		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoFindResult.iterator() ;
		// TODO: Shouldn't only one MovieAndShowInfo match for this find?
		// Let's count
		int numMovieAndShowInfosFound = 0 ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			++numMovieAndShowInfosFound ;
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			// Pre-condition: theMovieAndShowInfo is only present here if it needs to be remux'd/retranscoded
			log.info( getName() + " Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
			// theMovieAndShowInfo should have information about the movie or show along with
			// information about the constituent files (mkv and mp4) for it.

			boolean operationSuccess = false ;
			if( doRemux )
			{
				operationSuccess = remuxFile( theMovieAndShowInfo, fileToRemuxOrRetranscode ) ;
			}
			else
			{
				operationSuccess = transcodeFile( theMovieAndShowInfo, fileToRemuxOrRetranscode ) ;
			}

			if( !operationSuccess )
			{
				// Transcode/remux failed.
				log.warning(  getName() + " Operation failed" ) ;
				continue ;
			}
			// Post-condition: Transcode/remux succeeded
			// Replace the old mp4 file with the new mp4 file.

			// Replace the old mp4 file with the new file.
			File oldMP4File = fileToRemuxOrRetranscode.getMP4FinalFile() ;
			File newMP4File = new File( fileToRemuxOrRetranscode.getMP4OutputFileNameWithPath() ) ;

			// First, delete the old mp4 file.
			log.info( getName() + " Deleting oldMP4File: " + oldMP4File.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				try
				{
					oldMP4File.delete() ;
				}
				catch( Exception theException )
				{
					log.warning( getName() + " Error deleting oldMP4File (" + oldMP4File.getAbsolutePath() + "): " + theException.toString() ) ;
					continue ;
				}
			}

			// Modify the probe result to indicate the final destination of the mp4 file, versus its temporary
			//  output directory.
			// Since the mp4 file is different, update the probe.
			FFmpegProbeResult mp4ProbeResult = common.ffprobeFile( newMP4File, log ) ;

			// Move the new mp4 output file to its destination
			log.info( getName() + " Queueing move " + newMP4File.getAbsolutePath() + " -> " + oldMP4File.getAbsolutePath() ) ;
			moveFile( fileToRemuxOrRetranscode ) ;

			// No need to move the mkv or srt files since this program only deals with the mp4 file(s).
			// No need to update the mkv probe since the mkv has not changed.
			if( mp4ProbeResult != null )
			{
				// We probed the new mp4 file while it was sitting in a temporary directory. As a result, it
				// contains that temporary directory information.
				// We need to update that directory information before storing the probe in the database.
				mp4ProbeResult.setFileNameWithPath( oldMP4File.getAbsolutePath() ) ;
				mp4ProbeResult.setFileNameShort( Common.shortenFileName( oldMP4File.getAbsolutePath() ) ) ;

				// Update the MovieAndShowInfo with the new mp4 file information
				// Should be OK to update the probe result here because it is not being used anywhere else (presently)
				// in this or the calling algorithm.
				// NOTE: Shouldn't need to update the correlated file information since it only holds location information, which
				// is not changing.

				// Update the probe information table with the updated probe.
				Bson probeInfoIDFilter = Filters.eq( "fileNameWithPath", oldMP4File.getAbsolutePath() ) ;
				if( !common.getTestMode() )
				{
					// Should be ok to update the probe database.
					probeInfoCollection.replaceOne( probeInfoIDFilter, mp4ProbeResult ) ;
				}
			} // if( mp4ProbeResult != null )
			// No need to update the CorrelatedFile: that object only stores information about the location of the file,
			// but nothing about its contents, date of creation or modification, or probe information (probe information
			// is stored in the probe table in the database, but not in the CorrelatedFile in the database).
			log.info( getName() + " Found " + numMovieAndShowInfosFound + " matching MovieAndShowInfos for " + fileToRemuxOrRetranscode.toString() ) ;
		}
	}

	protected boolean transcodeFile( MovieAndShowInfo theMovieAndShowInfo,
			TranscodeFile fileToTranscode )
	{
		TranscodeCommon tCommon = new TranscodeCommon(
				log,
				common,
				fileToTranscode.getMKVInputDirectory(),
				fileToTranscode.getMKVFinalDirectory(),
				fileToTranscode.getMP4OutputDirectory(),
				fileToTranscode.getMP4FinalDirectory() ) ;

		// Transcode the file.
		fileToTranscode.setTranscodeInProgress();

		// No need to synchronize on the file system here since a transcode doesn't use much drive throughput.
		boolean transcodeSucceeded = tCommon.transcodeFile( fileToTranscode ) ;
		if( transcodeSucceeded )
		{
			fileToTranscode.setTranscodeComplete() ;
		}
		else
		{
			// Transcode failed.
			log.warning(  getName() + " Transcode failed" ) ;
			fileToTranscode.unSetTranscodeInProgress() ;
		}
		return transcodeSucceeded ;
	} // transcodeFile()

	protected void addMovieOrShowToMove( TranscodeFile movieOrShowToMove )
	{
		assert( movieOrShowToMove != null ) ;
		synchronized( moviesAndShowsToMove )
		{
			moviesAndShowsToMove.add( movieOrShowToMove ) ;
		}
	}

	protected void addMovieOrShowToRemux( TranscodeFile movieOrShowToRemux )
	{
		assert( movieOrShowToRemux != null ) ;
		synchronized( moviesAndShowsToRemux )
		{
			moviesAndShowsToRemux.add( movieOrShowToRemux ) ;
		}
	}

	protected void addMovieOrShowToRemux( List< TranscodeFile > moviesOrShowsToRemux )
	{
		assert( moviesOrShowsToRemux != null ) ;
		synchronized( moviesAndShowsToRemux )
		{
			moviesAndShowsToRemux.addAll( moviesOrShowsToRemux ) ;
		}
	}

	protected void addMovieOrShowToRetranscode( TranscodeFile movieOrShowToRetranscode )
	{
		assert( movieOrShowToRetranscode != null ) ;
		synchronized( moviesAndShowsToRetranscode )
		{
			moviesAndShowsToRetranscode.add( movieOrShowToRetranscode ) ;
		}
	}

	protected void addMovieOrShowToRetranscode( List< TranscodeFile > movieOrShowToRetranscode )
	{
		assert( movieOrShowToRetranscode != null ) ;
		synchronized( moviesAndShowsToRetranscode )
		{
			moviesAndShowsToRetranscode.addAll( movieOrShowToRetranscode ) ;
		}
	}

	public boolean atLeastOneThreadIsAlive( final Map< String, RemuxWithSubtitles > theThreads )
	{
		if( !isUseThreads() )
		{
			// Not using threads, so just return true
			return true ;
		}

		for( Map.Entry< String, RemuxWithSubtitles > entry : theThreads.entrySet() )
		{
			RemuxWithSubtitles rwsWorker = entry.getValue() ;
			if( rwsWorker.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}

	/**
	 * Return all MovieAndShowInfo instances, keyed by mp4LongPath.
	 * @return
	 */
	public Map< String, MovieAndShowInfo > getAllMovieAndShowInfosKeyedByMP4LongPath()
	{
		Map< String, MovieAndShowInfo > masInfoMap =
				new HashMap< String, MovieAndShowInfo >( (int) masMDB.getMovieAndShowInfoCollection().countDocuments() ) ;

		// First, get the entire MovieAndShowInfo collection
		FindIterable< MovieAndShowInfo > movieAndShowInfoIterable = masMDB.getMovieAndShowInfoCollection().find() ;

		// Walk through the entire collection
		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoIterable.iterator() ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			// Add each entry into the return Map
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			String mp4LongPath = theMovieAndShowInfo.getMP4LongPath() ;
			masInfoMap.put( mp4LongPath, theMovieAndShowInfo ) ;
		}
		return masInfoMap ;
	}

	/**
	 * Given an absolute path name, return just the machine name and folder.
	 * For example:
	 *  - Input: \\\\yoda\\MP4\\Movies\\Transformers (2009)\\Transformers (2009).mkv
	 *  - Output: \\\\yoda\\MP4
	 * @param fileNameWithPath
	 * @return
	 */
	public String getDriveNameFromPath( final String fileNameWithPath )
	{
		final Path thePath = Paths.get( fileNameWithPath ) ;
		final Path rootPath = thePath.getRoot() ;
		String pathWithoutEndingBackslash = Common.removeTrailingBackslash( rootPath.toString() ) ;
		return pathWithoutEndingBackslash ;
	}

	public long getFileSyncSleepTime() {
		return fileSyncSleepTime;
	}

	public String getLogFileName()
	{
		return logFileName ;
	}

	/**
	 * Get the next movie or show to move.
	 * @return Next movie or show to move, or null if none exist.
	 */
	protected TranscodeFile getMovieOrShowToMove()
	{
		TranscodeFile retMe = null ;
		synchronized( moviesAndShowsToMove )
		{
			if( !moviesAndShowsToMove.isEmpty() )
			{
				retMe = moviesAndShowsToMove.remove( 0 ) ;
			}
		}
		return retMe ;
	}

	/**
	 * Get the next movie or show to remux.
	 * @return Next movie or show to remux, or null if none exist.
	 */
	protected TranscodeFile getMovieOrShowToRemux()
	{
		TranscodeFile retMe = null ;
		synchronized( moviesAndShowsToRemux )
		{
			if( !moviesAndShowsToRemux.isEmpty() )
			{
				retMe = moviesAndShowsToRemux.remove( 0 ) ;
			}
		}
		return retMe ;
	}

	/**
	 * Get the next movie or show to retranscode.
	 * @return Next movie or show to retranscode, or null if none exist.
	 */
	protected TranscodeFile getMovieOrShowToRetranscode()
	{
		TranscodeFile retMe = null ;
		synchronized( moviesAndShowsToRetranscode )
		{
			if( !moviesAndShowsToRetranscode.isEmpty() )
			{
				retMe = moviesAndShowsToRetranscode.remove( 0 ) ;
			}
		}
		return retMe ;
	}

	public String getMP4OutputDirectory()
	{
		return mp4OutputDirectory ;
	}

	/**
	 * Find the mp4 thread associated with the given mp4FileNameWithPath.
	 * @param mp4FileNameWithPath
	 * @return RemuxWithSubtitles object that matches the mp4 file name, or null if none found.
	 */
	protected RemuxWithSubtitles getMP4Thread( final String mp4FileNameWithPath )
	{
		if( !isUseThreads() )
		{
			// Using a single thread (this)
			log.info( getName() + " Not using threads, so returning this." ) ;
			return this ;
		}

		RemuxWithSubtitles retMe = null ;
		final String mp4SearchDriveName = getDriveNameFromPath( mp4FileNameWithPath ) ;
		for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
		{
			final String mp4DriveName = entry.getKey() ;
			if( mp4SearchDriveName.equalsIgnoreCase( mp4DriveName ) )
			{
				// Found a match
				retMe = entry.getValue() ;
				log.info( "Found match: " + mp4SearchDriveName + " with " + retMe.getName() ) ;
				break ;
			}
		}

		if( null == retMe )
		{
			log.warning(  getName() + " Unable to find mp4 thread matching file: " + mp4FileNameWithPath ) ;
		}
		return retMe ;
	}

	/**
	 * Return all FFmpegProbeResults from the database, keyed by the long file name.
	 * @return
	 */
	public Map< String, FFmpegProbeResult > getAllProbeInfoResults()
	{
		Map< String, FFmpegProbeResult > probeInfoMap =
				new HashMap< String, FFmpegProbeResult >( (int) probeInfoCollection.countDocuments() ) ;

		// First, get the entire probe collection
		FindIterable< FFmpegProbeResult > probeInfoIterable = probeInfoCollection.find() ;

		// Walk through the entire collection
		Iterator< FFmpegProbeResult > probeInfoIterator = probeInfoIterable.iterator() ;
		while( probeInfoIterator.hasNext() )
		{
			// Add each entry into the return Map
			FFmpegProbeResult theProbeResult = probeInfoIterator.next() ;
			String getFileNameWithPath = theProbeResult.getFileNameWithPath() ;
			probeInfoMap.put( getFileNameWithPath, theProbeResult ) ;
		}
		return probeInfoMap ;
	}

	protected String getStopFileName()
	{
		return stopFileName ;
	}

	public String getTranscodeWorkerThreadName()
	{
		return transcodeWorkerThreadName ;
	}

	/**
	 * Check each of the worker threads. If all of their work is done, then return true. Return false otherwise.
	 * @return
	 */
	public boolean isAllWorkDone()
	{
		boolean allWorkDone = true ;
		for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
		{
			RemuxWithSubtitles rwsWorker = entry.getValue() ;

			// To demonstrate that all work is not done, I need only find a single job undone.
			if( !rwsWorker.moviesAndShowsToMoveIsEmpty()
					|| !rwsWorker.moviesAndShowsToRemuxIsEmpty()
					|| !rwsWorker.moviesAndShowsToRetranscodeIsEmpty() )
			{
				// This thread has at least one unit of work remaining. Work is not done.
				allWorkDone = false ;
				break ;
			}
		}
		return allWorkDone ;
	}

	protected boolean isKeepRunning()
	{
		return keepRunning ;
	}

	public boolean isUseThreads()
	{
		return useThreads ;
	}

	public boolean moviesAndShowsToMoveIsEmpty()
	{
		boolean isEmpty = false ;
		synchronized( moviesAndShowsToMove )
		{
			isEmpty = moviesAndShowsToMove.isEmpty() ;
		}
		return isEmpty ;
	}

	public boolean moviesAndShowsToRemuxIsEmpty()
	{
		boolean isEmpty = false ;
		synchronized( moviesAndShowsToRemux )
		{
			isEmpty = moviesAndShowsToRemux.isEmpty() ;
		}
		return isEmpty ;
	}

	public boolean moviesAndShowsToRetranscodeIsEmpty()
	{
		boolean isEmpty = false ;
		synchronized( moviesAndShowsToRetranscode )
		{
			isEmpty = moviesAndShowsToRetranscode.isEmpty() ;
		}
		return isEmpty ;
	}

	public void setFileSyncSleepTime( long fileSyncSleepTime )
	{
		this.fileSyncSleepTime = fileSyncSleepTime ;
	}

	protected void setKeepRunning( boolean keepRunning )
	{
		this.keepRunning = keepRunning;
	}

	public void setUseThreads( boolean useThreads )
	{
		this.useThreads = useThreads;
	}

	/**
	 * Set the reference to the workerThreads object for this RemuxWithSubtitles instance. This will be used to
	 *  communicate with the worker thread when a file needs to be moved.
	 * @param workerThreads
	 */
	private void setWorkerThreads( Map< String, RemuxWithSubtitles > workerThreads )
	{
		this.workerThreads = workerThreads ;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepRunning()) ;
	}
}
