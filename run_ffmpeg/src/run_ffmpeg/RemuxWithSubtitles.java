package run_ffmpeg;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.ArrayList ;
import java.util.Iterator;
import java.util.logging.Logger;
import java.io.File ;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

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

	/// Used to store synchronized locks to each drive.
	//	private SortedMap< String, Object > driveLocks = new TreeMap< String , Object>() ;

	/// Move file thread controller.
	MoveFiles moveFiles = null ;

	/// Handles to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	/// The folders containing movies/shows to remux/retranscode
	private List< TranscodeFile > moviesAndShowsToRemux = new ArrayList< TranscodeFile >() ;
	private List< TranscodeFile > moviesAndShowsToRetranscode = new ArrayList< TranscodeFile >() ;
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

		// Create the MoveFiles handler, which will also start the threads.
		moveFiles = new MoveFiles( log, common ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		probeInfoCollection = masMDB.getProbeInfoCollection() ;
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
		setUseThreads( false ) ;
		common.setTestMode( true ) ;
		//		buildDriveLocks() ;

		// Get all of the MP4 drives
		List< String > mp4DrivesWithoutFolders = common.getAllMP4Drives() ;
		// If I want to specify a single folder to remux/retranscode, then add it here.
		//		List< String > mp4DrivesWithoutFolders = new ArrayList< String >() ;
		// This only works on MP4 files and folders, and the folder name must match the database entry
		//  (\\yoda\MP4 instead of "Q:\")
		//		mp4DrivesWithoutFolders.add( "\\\\yoda\\MP4" ) ;

		// Add "Movies" and "TV Shows" to each of MP4 drives
		List< String > mp4Drives = common.addMoviesAndTVShowFoldersToEachDrive( mp4DrivesWithoutFolders ) ;
		log.info( "Will remux/retranscode the following top level folders: " + mp4Drives.toString() ) ;

		// Find the lowest level folders in each directory tree. This will be used for searching for MovieAndShowInfos.
		// The list of folders will be unique -- no duplicates.
		List< String > mp4MovieAndShowInfoFolders = new ArrayList< String >() ;
		for( String mp4DriveFolder : mp4Drives )
		{
			log.info( "Finding all subdirectories in " + mp4DriveFolder + "; this may take a minute." ) ;
			mp4MovieAndShowInfoFolders.addAll( common.findLowestLevelDirectories( mp4DriveFolder ) ) ;
		}

		log.info( "Remuxing/retranscoding " + mp4MovieAndShowInfoFolders.size() + " movies/tv shows "
				+ (useThreads ? "with" : "without" ) + " threads" ) ;

		// Maintain a list of files to remux and one for files to transcode
		List< TranscodeFile > filesToRemux = new ArrayList< TranscodeFile >() ;
		List< TranscodeFile > filesToTranscode = new ArrayList< TranscodeFile >() ;

		log.info( "Building remux and transcode list; this could take a minute." ) ;
		// Iterate through each mp4Drive and add the files to remux and transcode
		for( String mp4MovieAndShowInfoFolder : mp4MovieAndShowInfoFolders )
		{
			// This method invocation will add the files to each of the lists as appropriate
			buildRemuxAndTranscodeList( mp4MovieAndShowInfoFolder, filesToRemux, filesToTranscode ) ;
		}
		log.info( "remuxing " + filesToRemux.size() + " file(s) and retranscoding " + filesToTranscode.size() + " file(s)" ) ;

		// This is the important part: remuxing involves reading at max speed from an mkv drive, then
		//  writing at max speed to an mp4 drive.
		// Since I can't really know ahead of time which MP4 files correlate to which MKV drives, I need
		//  to synchronize the use of those resources so they are not max taxed, which might lead to
		//  a process failure.
		Map< String, RemuxWithSubtitles > workerThreads = buildWorkerThreads( filesToRemux, filesToTranscode ) ;

		// If using threads, then start the threads.
		if( isUseThreads() )
		{
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				log.info( "Starting new thread" ) ;
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				rwsWorker.start() ;
			}
		}

		// Main loop.
		// If not using threads, execute the workers sequentially until they are all complete, then shutdown.
		// If using threads, then  sleep until they are all done with their work or we receive notification
		//  to shutdown.
		while( shouldKeepRunning() && atLeastOneThreadIsAlive( workerThreads ) )
		{
			if( !isUseThreads() )
			{
				if( !workerThreads.isEmpty() )
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
						log.info( "Running new worker..." ) ;
						rwsWorker.run() ;
					}
					// Now that we have walked through each worker thread, all work is technically done.
					// Shutdown.
					setKeepRunning( false ) ;
				}
			}
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
					log.warning( "Exception: " + theException.toString() ) ;
				}
			}
		}
		log.info( "Shutting down..." ) ;

		// Stop threads
		if( isUseThreads() )
		{
			log.info( "Stopping threads..." ) ;	
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				log.info( "Stopping thread" ) ;
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				rwsWorker.setKeepRunning( false ) ;
			}
		}

		// Join threads
		if( isUseThreads() )
		{
			log.info( "Joining threads..." ) ;	
			for( Map.Entry< String, RemuxWithSubtitles > entry : workerThreads.entrySet() )
			{
				RemuxWithSubtitles rwsWorker = entry.getValue() ;
				try
				{
					log.info( "Joining thread..." ) ;
					rwsWorker.join() ;
					log.info( "Joined." ) ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception: " + theException.toString() ) ;
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
		// In order to prevent backloading all of the remux actions across the threads,
		// have each thread alternate between remux and retranscode.
		while( shouldKeepRunning() && (!moviesAndShowsToRemuxIsEmpty() || !moviesAndShowsToRetranscodeIsEmpty()) )
		{
			if( !moviesAndShowsToRemuxIsEmpty() && shouldKeepRunning() )
			{
				// Handle remux
				TranscodeFile theFileToRemux = getMovieOrShowToRemux() ;
				if( null == theFileToRemux )
				{
					// All's fair in multi-threading.
					log.warning( "Found null file to remux" ) ;
				}
				else
				{
					remuxOrRetranscodeFile( theFileToRemux, true ) ;
				}
			} // if( remux )

			if( !moviesAndShowsToRetranscodeIsEmpty() && shouldKeepRunning() )
			{
				TranscodeFile theFileToRetranscode = getMovieOrShowToRetranscode() ;
				if( null == theFileToRetranscode )
				{
					// All's fair in multi-threading.
					log.warning( "Found null file to retranscode" ) ;
				}
				else
				{
					remuxOrRetranscodeFile( theFileToRetranscode, false ) ;
				}
			} // if( transcode )
		} // while()
	}

	/**
	 * Instantiate the locks that will be used to guard use of the drive via synchronized{}.
	 * One for each drive with dummy objects that serve as the lock.
	 */
	//	private void buildDriveLocks()
	//	{
	//		List< String > allDrives = common.getAllMKVDrives() ;
	//		allDrives.addAll( common.getAllMP4Drives() ) ;
	//
	//		for( String drive : allDrives )
	//		{
	//			String driveLock = new String( drive ) ;
	//			driveLocks.put( drive, driveLock ) ;
	//		}
	//	}

	/**
	 * Add each TV Show and Movie mkv that needs to be remuxed or re-transcoded to the
	 *  either of the provided files list.
	 * @param mp4DriveWithFolder
	 * @param filesToRemux
	 * @param filesToTranscode
	 */
	protected void buildRemuxAndTranscodeList( final String mp4DriveWithFolder,
			List< TranscodeFile > filesToRemux,
			List< TranscodeFile > filesToTranscode )
	{
		Bson findFilesFilter = Filters.eq( "mp4LongPath", mp4DriveWithFolder ) ;
		FindIterable< MovieAndShowInfo > movieAndShowInfoFindResult = movieAndShowInfoCollection.find( findFilesFilter ) ;

		// Iterate through those shows and check for new SRT file(s)
		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoFindResult.iterator() ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			log.fine( "Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
			if( theMovieAndShowInfo.getMKVLongPath().contains( "MKV_Archive4" ) )
			{
				// Still having a problem with MKV_4; ignore it for now.
				continue ;
			}
			// theMovieAndShowInfo should have information about the movie or show along with
			// information about the constituent files (mkv and mp4) for it.

			// Retrieve an iterator to the correlated files for this movie or show
			for( CorrelatedFile theCorrelatedFile : theMovieAndShowInfo.getCorrelatedFilesList() )
			{
				TranscodeFile fileToRemuxOrTranscode = buildTranscodeFile( theMovieAndShowInfo, theCorrelatedFile ) ;
				if( null == fileToRemuxOrTranscode )
				{
					// Error reading this MovieAndShowInfo
					log.warning( "Error in buildTranscodeFile for MovieAndShowInfo: " + theMovieAndShowInfo.toString() + ", skipping" ) ;
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
					log.warning( "Invalid return of " + needRemuxOrTranscodeStatus + " status from needRemuxOrTranscode "
							+ " for file: " + fileToRemuxOrTranscode.toString() ) ;
				}
			} // for( correlatedFile )
		} // while( movieAndShowInfoIterator.hasNext() )
	} // buildRemuxAndTranscodeList()

	protected TranscodeFile buildTranscodeFile( MovieAndShowInfo theMovieAndShowInfo, CorrelatedFile theCorrelatedFile )
	{
		//		log.info( "Processing movie or show: " + theMovieAndShowInfo.getMovieOrShowName() + ", file: " + theCorrelatedFile.toString() ) ;

		// First determine if the both mp4 and mkv files exist.
		if( theCorrelatedFile.isMissingFile() )
		{
			log.warning( "Missing file in correlated file: " + theCorrelatedFile ) ;
			return null ;
		}
		if( (theCorrelatedFile.getNumberOfMKVFiles() != 1) ||
				(theCorrelatedFile.getNumberOfMP4Files() != 1) )
		{
			log.warning( "More than one MKV or MP4 files in correlated file: " + theCorrelatedFile.toString() ) ;
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
		log.info( "Creating worker threads, one per MP4 drive." ) ;

		// Create a new RWS for each MKV drive.
		List< String > mp4Drives = common.getAllMP4Drives() ;
		for( String mp4Drive : mp4Drives )
		{
			// Build a new RWS object to handle this mkv drive.
			RemuxWithSubtitles rwsWorker = new RemuxWithSubtitles() ;
			//			rwsWorker.setDriveLocks( this.driveLocks ) ;

			// Populate the RWS object with all remux & transcode files for that drive.
			int numRemuxEntries = 0 ;
			int numTranscodeEntries = 0 ;

			// Start with remux files.
			for( TranscodeFile theTranscodeFile : filesToRemux )
			{
				final String mp4Path = theTranscodeFile.getMP4FinalFileNameWithPath() ;
				if( mp4Path.contains( mp4Drive ) )
				{
					rwsWorker.addMovieOrShowToRemux( theTranscodeFile ) ;
					++numRemuxEntries ;
				}
			}

			// Continue with transcode files.
			for( TranscodeFile theTranscodeFile : filesToTranscode )
			{
				final String mp4Path = theTranscodeFile.getMP4FinalFileNameWithPath() ;
				if( mp4Path.contains( mp4Drive ) )
				{
					rwsWorker.addMovieOrShowToRetranscode( theTranscodeFile ) ;
					++numTranscodeEntries ;
				}
			}
			log.info( "Worker thread for " + mp4Drive + " contains " + numRemuxEntries + " items to remux, and "
					+ numTranscodeEntries + " items to retranscode" ) ;

			// Finally add the worker to the threadMap.
			threadMap.put( mp4Drive, rwsWorker ) ;
		}		
		return threadMap ;
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
			//			final long oldMP4FileLastModified = oldMP4File.lastModified() ;
			//			final long srtFileLastModified = theSRTFile.lastModified() ;

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

	/**
	 * Process the given correlated file, meaning to transcode or remux it.
	 * Need to be careful about changing anything -- the caller is using a loop to iterate through the correlated files
	 *  so we can't change the data in any way that will interrupt that algorithm.
	 * Return true if anything was changed -- this will indicate that the caller needs to update the database.
	 * @param theMovieAndShowInfo
	 * @param theCorrelatedFile
	 * @return
	 */
	/*	public boolean processCorrelatedFile( MovieAndShowInfo theMovieAndShowInfo, CorrelatedFile theCorrelatedFile )
	{
		log.info( "Processing movie or show: " + theMovieAndShowInfo.getMovieOrShowName() + ", file: " + theCorrelatedFile.toString() ) ;

		// First determine if the both mp4 and mkv files exist.
		if( theCorrelatedFile.isMissingFile() )
		{
			log.warning( "Missing file in correlated file: " + theCorrelatedFile ) ;
			return false ;
		}
		if( (theCorrelatedFile.getNumberOfMKVFiles() != 1) ||
				(theCorrelatedFile.getNumberOfMP4Files() != 1) )
		{
			log.warning( "More than one MKV or MP4 files in correlated file: " + theCorrelatedFile.toString() ) ;
			return false ;
		}
		// Post condition: No missing files, and only one mp4 and one mkv file.

		final String mkvFileName = theMovieAndShowInfo.getMKVLongPath() + "\\" + theCorrelatedFile.getFileName() + ".mkv" ;
		final String mp4FileName = theMovieAndShowInfo.getMP4LongPath() + "\\" + theCorrelatedFile.getFileName() + ".mp4" ;
		final File mkvFile = new File( mkvFileName ) ;
		final File oldMP4File = new File( mp4FileName ) ;

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
		boolean needTranscode = false ;
		boolean needRemux = false ;

		// Walk through the list looking for srt files that are newer than the MP4 file.
		// Also look for a forced_subtitle file. If found and newer than the MP4 file, then the mkv
		//  also needs to be transcoded.
		for( Iterator< File > srtFileIterator = theTranscodeFile.getRealSRTFileListIterator() ; srtFileIterator.hasNext() ; )
		{
			final File theSRTFile = srtFileIterator.next() ;
			final String theSRTFileName = theSRTFile.getName() ;
			//			final long oldMP4FileLastModified = oldMP4File.lastModified() ;
			//			final long srtFileLastModified = theSRTFile.lastModified() ;

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
				needTranscode = true ;
			}
			else
			{
				// Not a forced subtitle srt file, but the srt file is still newer than the mp4 file.
				needRemux = true ;
			}
		} // for( iterator )

		// Important that the check for needTranscode occur first as that is the less frequent case.
		boolean operationSuccess = false ;
		if( needTranscode )
		{
			operationSuccess = transcodeFile( theMovieAndShowInfo, theCorrelatedFile, theTranscodeFile ) ;
		}
		else if( needRemux )
		{
			operationSuccess = remuxFile( theMovieAndShowInfo, theCorrelatedFile, theTranscodeFile ) ;
		}
		else
		{
			// This file does *not* need to be remuxed or re-transcoded
			return false ;
		}

		if( !operationSuccess )
		{
			// Transcode/remux failed.
			log.warning( "Operation failed" ) ;
			return false ;
		}
		// Post-condition: Transcode/remux succeeded
		// Replace the old mp4 file with the new mp4 file.

		// Replace the old mp4 file with the new file.
		File newMP4File = new File( theTranscodeFile.getMP4OutputFileNameWithPath() ) ;

		// First, delete the old mp4 file.
		if( !common.getTestMode() )
		{
			oldMP4File.delete() ;
		}

		// Modify the probe result to indicate the final destination of the mp4 file, versus its temporary
		//  output directory.
		// Since the mp4 file is different, update the probe.
		FFmpegProbeResult mp4ProbeResult = common.ffprobeFile( newMP4File, log ) ;

		// Move the new mp4 output file to its destination
		log.info( "Moving " + newMP4File.getAbsolutePath() + " -> " + oldMP4File.getAbsolutePath() ) ;
		if( !common.getTestMode() )
		{
			moveFiles.addMP4FileMove( newMP4File.getAbsolutePath(),
					oldMP4File.getAbsolutePath() ) ;
		}

		// No need to move the mkv or srt files
		// No need to update the mkv probe
		if( mp4ProbeResult != null )
		{
			mp4ProbeResult.setFileNameWithPath( oldMP4File.getAbsolutePath() ) ;
			mp4ProbeResult.setFileNameShort( Common.shortenFileName( oldMP4File.getAbsolutePath() ) ) ;

			// Update the MovieAndShowInfo with the new mp4 file information
			// Should be OK to update the probe result here because it is not being used anywhere else (presently)
			// in this or the calling algorithm.
			theMovieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;

			// Update the probe information table with the updated probe.
			Bson probeInfoIDFilter = Filters.eq( "fileNameWithPath", oldMP4File.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				// Should be ok to update the probe database.
				probeInfoCollection.replaceOne( probeInfoIDFilter, mp4ProbeResult ) ;
			}
		} // if( mp4ProbeResult != null )

		return true ;
	} // processCorrelatedFile()
	 */

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
		//		Object driveLock = getDriveLock( getDriveNameFromPath( fileToTranscode.getMP4FinalFileNameWithPath() ) ) ;
		boolean remuxSucceeded = false ;
		//		synchronized( driveLock )
		//		{
		remuxSucceeded = tCommon.remuxFile( fileToTranscode ) ;
		//		}
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
		log.info( "Running find on file (" + (doRemux ? "remux" : "retranscode") +"): " + fileToRemuxOrRetranscode ) ;

		final String mp4LongPath = fileToRemuxOrRetranscode.getMP4FinalDirectory() ;
		Bson findFileFilter = Filters.eq( "mp4LongPath", mp4LongPath ) ;
		FindIterable< MovieAndShowInfo > movieAndShowInfoFindResult = movieAndShowInfoCollection.find( findFileFilter ) ;

		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoFindResult.iterator() ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			// Pre-condition: theMovieAndShowInfo is only present here if it needs to be remux'd/retranscoded
			log.info( "Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
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
				log.warning( "Operation failed" ) ;
				return ;
			}
			// Post-condition: Transcode/remux succeeded
			// Replace the old mp4 file with the new mp4 file.

			// Replace the old mp4 file with the new file.
			File oldMP4File = fileToRemuxOrRetranscode.getMP4FinalFile() ;
			File newMP4File = new File( fileToRemuxOrRetranscode.getMP4OutputFileNameWithPath() ) ;

			// First, delete the old mp4 file.
			if( !common.getTestMode() )
			{
				oldMP4File.delete() ;
			}

			// Modify the probe result to indicate the final destination of the mp4 file, versus its temporary
			//  output directory.
			// Since the mp4 file is different, update the probe.
			FFmpegProbeResult mp4ProbeResult = common.ffprobeFile( newMP4File, log ) ;

			// Move the new mp4 output file to its destination
			log.info( "Moving " + newMP4File.getAbsolutePath() + " -> " + oldMP4File.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				// Synchronize on the lock object for the destination drive. This assumes that the
				// mp4 output drive is local and has sufficient throughput for its read to be minimally
				// intrusive. However, the destination drive will probably be on the RPi, which is
				// really slow...need to guard the Pi's drives.
				//				Object destinationDriveSyncLock = getDriveLock( getDriveNameFromPath( oldMP4File.getAbsolutePath() )  ) ;
				//				synchronized( destinationDriveSyncLock )
				//				{
				// This will be a synchronous move (no threads).
				moveFiles.moveFile( newMP4File.getAbsolutePath(),
						oldMP4File.getAbsolutePath() ) ;
				//				}
			}

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
				//				theMovieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;

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
			log.warning( "Transcode failed" ) ;
			fileToTranscode.unSetTranscodeInProgress() ;
		}
		return transcodeSucceeded ;
	} // transcodeFile()

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

	//	public Object getDriveLock( final String theDrive )
	//	{
	//		Object theLock = driveLocks.get( theDrive ) ;
	//		if( null == theLock )
	//		{
	//			log.warning( "Unable to find lock for drive: " + theDrive ) ;
	//			theLock = new String( "Broken Lock" ) ;
	//		}
	//		return theLock ;
	//	}

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
		return mp4OutputDirectory;
	}

	//	public TranscodeFile getNextMovieAndShowToRemux()
	//	{
	//		TranscodeFile retMe = null ;
	//		synchronized( moviesAndShowsToRemux )
	//		{
	//			retMe = moviesAndShowsToRemux.remove( 0 ) ;
	//		}
	//		return retMe ;
	//	}
	//
	//	public TranscodeFile getNextMovieAndShowToRetranscode()
	//	{
	//		TranscodeFile retMe = null ;
	//		synchronized( moviesAndShowsToRetranscode )
	//		{
	//			retMe = moviesAndShowsToRetranscode.remove( 0 ) ;
	//		}
	//		return retMe ;
	//	}

	protected String getStopFileName()
	{
		return stopFileName;
	}

	protected boolean isKeepRunning()
	{
		return keepRunning;
	}

	public boolean isUseThreads()
	{
		return useThreads;
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

	//	protected void setDriveLocks( SortedMap< String, Object > newDriveLocks )
	//	{
	//		this.driveLocks = newDriveLocks ;
	//	}

	protected void setKeepRunning( boolean keepRunning )
	{
		this.keepRunning = keepRunning;
	}

	public void setUseThreads( boolean useThreads )
	{
		this.useThreads = useThreads;
	}

	public boolean shouldKeepRunning()
	{
		return (!common.shouldStopExecution( getStopFileName() ) && isKeepRunning()) ;
	}

}
