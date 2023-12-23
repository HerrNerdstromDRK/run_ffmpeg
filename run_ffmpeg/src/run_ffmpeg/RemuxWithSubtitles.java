package run_ffmpeg;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.ArrayList ;
import java.util.Iterator;
import java.util.logging.Logger;
import java.io.File ;

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
	private SortedMap< String, Object > driveLocks = new TreeMap< String , Object>() ;

	/// Move file thread controller.
	MoveFiles moveFiles = null ;

	/// Handles to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	private String driveToRemux = null ;
	private boolean keepRunning = true ;

	public RemuxWithSubtitles()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Create the MoveFiles handler, which will also start the threads.
		//		Logger moveFilesLogger = Common.setupLogger( "MoveFiles", MoveFiles.getLogFileName() ) ;
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
		common.setTestMode( false ) ;
		buildDriveLocks() ;

		// Get all of the MP4 drives
		//		List< String > mp4Drives = common.getAllMP4Drives() ;
		List< String > mp4DrivesWithoutFolders = new ArrayList< String >() ;
		// This only works on MP4 files and folders, and the folder name must match the database entry
		//  (\\yoda\MP4 instead of "Q:\")
		mp4DrivesWithoutFolders.add( "\\\\yoda\\MP4" ) ;

		// Add "Movies" and "TV Shows" to each of MP4 drives
		List< String > mp4Drives = common.addMoviesAndTVShowFoldersToEachDrive( mp4DrivesWithoutFolders ) ;
		log.info( "Will remux/retranscode the following top level folders: " + mp4Drives.toString() ) ;

		// Find the lowest level folders in each directory tree. This will be used for searching for MovieAndShowInfos.
		// The list of folders will be unique -- no duplicates.
		List< String > mp4MovieAndShowInfoFolders = new ArrayList< String >() ;
		for( String mp4DriveFolder : mp4Drives )
		{
			mp4MovieAndShowInfoFolders.addAll( common.findLowestLevelDirectories( mp4DriveFolder ) ) ;
		}
//		log.info( "Remuxing/retranscoding " + mp4MovieAndShowInfoFolders.size() + " movies/tv shows "
//				+ (useThreads ? "with" : "without" ) + " threads" ) ;

		// Maintain a list of files to remux and one for files to transcode
		List< TranscodeFile > filesToRemux = new ArrayList< TranscodeFile >() ;
		List< TranscodeFile > filesToTranscode = new ArrayList< TranscodeFile >() ;

		// Iterate through each mp4Drive and add the files to remux and transcode
		for( String mp4MovieAndShowInfoFolder : mp4MovieAndShowInfoFolders )
		{
			// This method invocation will add the files to each of the lists as appropriate
			buildRemuxAndTranscodeList( mp4MovieAndShowInfoFolder, filesToRemux, filesToTranscode ) ;
		}
		log.info( "remuxing " + filesToRemux.size() + " file(s) and retranscoding " + filesToTranscode + " file(s)" ) ;
		
		// This is the important part: remuxing involves reading at max speed from an mkv drive, then
		//  writing at max speed to an mp4 drive.
		// Since I can't really know ahead of time which MP4 files correlate to which MKV drives, I need
		//  to synchronize the use of those resources so they are not max taxed, which might lead to
		//  a process failure.

		List< RemuxWithSubtitles > remuxThreads = new ArrayList< RemuxWithSubtitles >() ;
		//
		//		for( String mp4Drive : mp4DrivesWithFolders )
		//		{
		//			RemuxWithSubtitles rwsWorker = new RemuxWithSubtitles() ;
		//			rwsWorker.setDriveToRemux( mp4Drive ) ;
		//			remuxThreads.add( rwsWorker ) ;
		//		}

		for( RemuxWithSubtitles rwsWorker : remuxThreads )
		{
			if( isUseThreads() )
			{
				log.info( "Starting thread: " + rwsWorker.getDriveToRemux() ) ;
				rwsWorker.start() ;
			}
		}

		while( shouldKeepRunning() && atLeastOneThreadIsAlive( remuxThreads ) )
		{
			if( !isUseThreads() )
			{
				if( !remuxThreads.isEmpty() )
				{
					// Not using threads.
					// Run the first rwsWorker, if it exists
					RemuxWithSubtitles rwsWorker = remuxThreads.remove( 0 ) ;
					if( rwsWorker != null )
					{
						log.info( "Running new worker..." ) ;
						rwsWorker.run() ;
					}
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

		// Stop threads
		if( isUseThreads() )
		{
			log.info( "Stopping threads..." ) ;	
			for( RemuxWithSubtitles rwsWorker : remuxThreads )
			{
				rwsWorker.setKeepRunning( false ) ;
			}
		}

		// Join threads
		if( isUseThreads() )
		{
			log.info( "Joining threads..." ) ;	
			for( RemuxWithSubtitles rwsWorker : remuxThreads )
			{
				try
				{
					rwsWorker.join() ;
				}
				catch( Exception theException )
				{
					log.warning( "Exception: " + theException.toString() ) ;
				}
			}
		}
	}

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
			log.info( "Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
			// theMovieAndShowInfo should have information about the movie or show along with
			// information about the constituent files (mkv and mp4) for it.

			// Retrieve an iterator to the correlated files for this movie or show
			for( CorrelatedFile theCorrelatedFile : theMovieAndShowInfo.getCorrelatedFilesList() )
			{
				TranscodeFile fileToRemuxOrTranscode = buildTranscodeFile( theMovieAndShowInfo, theCorrelatedFile ) ;
				if( null == fileToRemuxOrTranscode )
				{
					// Error reading this MovieAndShowInfo
					log.warning( "Error in buildTranscodeFile for MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
					continue ;
				}

				// At this point, the data stored in the database meets the file system.
				final int needRemuxOrTranscodeStatus = needRemuxOrTranscode( fileToRemuxOrTranscode,
						fileToRemuxOrTranscode.getMP4FinalFile() ) ;
				if( 0 == needRemuxOrTranscodeStatus )
				{
					// Nothing to do.
				}
				else if( 1 == needRemuxOrTranscodeStatus )
				{
					// Needs remux.
					filesToRemux.add( fileToRemuxOrTranscode );
				}
				else if( 2 == needRemuxOrTranscodeStatus )
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

	/**
	 * Method to remux everything on a single drive. This may be run as an individual thread, or sequentially
	 *  if single threaded.
	 */
	@Override
	public void run()
	{
		if( null == getDriveToRemux() )
		{
			log.warning( "null drive to remux" ) ;
			return ;
		}
		// Add "Movies" and "TV Shows" to the list of folders to remux
		//		List< String > driveToRemuxAsList = new ArrayList< String >() ;
		//		driveToRemuxAsList.add( getDriveToRemux() ) ;
		List< String > foldersToRemux = new ArrayList< String >() ; //common.addMoviesAndFoldersToEachDrive( driveToRemuxAsList ) ;
		foldersToRemux.add( "\\\\yoda\\MP4\\Movies\\Avatar (2009)" ) ;

		for( String folderToRemux : foldersToRemux )
		{
			if( shouldKeepRunning() )
			{
				remuxFolder( folderToRemux ) ;
			}
		}
	}

	private void remuxFolder( final String folderToRemux )
	{
		// Each folder should be of the form "\\\\yoda\\MP4\\Movies\\Movie Name (yyyy)" (or TV Show\\Season xx)
		// Use that path to search the database by mp4LongPath
		log.info( "Running find on folder: " + folderToRemux ) ;

		Bson findFilesFilter = Filters.eq( "mp4LongPath", folderToRemux ) ;
		FindIterable< MovieAndShowInfo > movieAndShowInfoFindResult = movieAndShowInfoCollection.find( findFilesFilter ) ;

		Iterator< MovieAndShowInfo > movieAndShowInfoIterator = movieAndShowInfoFindResult.iterator() ;
		while( movieAndShowInfoIterator.hasNext() )
		{
			boolean changedTheMovieAndShowInfo = false ;
			MovieAndShowInfo theMovieAndShowInfo = movieAndShowInfoIterator.next() ;
			log.info( "Found MovieAndShowInfo: " + theMovieAndShowInfo.toString() ) ;
			// theMovieAndShowInfo should have information about the movie or show along with
			// information about the constituent files (mkv and mp4) for it.

			// Retrieve an iterator to the correlated files for this movie or show
			for( CorrelatedFile theCorrelatedFile : theMovieAndShowInfo.getCorrelatedFilesList() )
			{
				//				CorrelatedFile theCorrelatedFile = correlatedFileIterator.next() ;
				boolean madeAChange = processCorrelatedFile( theMovieAndShowInfo, theCorrelatedFile ) ;
				if( madeAChange )
				{
					changedTheMovieAndShowInfo = true ;
				}
			}

			if( changedTheMovieAndShowInfo )
			{
				theMovieAndShowInfo.makeReadyCorrelatedFilesList() ;

				// Update the MovieAndShow table with the new MP4 file information.
				Bson movieAndShowInfoIDFilter = Filters.eq( "movieOrShowName", theMovieAndShowInfo.getMovieOrShowName() ) ;
				if( !common.getTestMode() )
				{
					movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter, theMovieAndShowInfo ) ;
				}
			}
		}
	}

	/**
	 * Instantiate the locks that will be used to guard use of the drive via synchronized{}.
	 * One for each drive with dummy objects that serve as the lock.
	 */
	private void buildDriveLocks()
	{
		List< String > allDrives = common.getAllMKVDrives() ;
		allDrives.addAll( common.getAllMP4Drives() ) ;
		
		for( String drive : allDrives )
		{
			String driveLock = new String( drive ) ;
			driveLocks.put( drive, driveLock ) ;
		}
	}
	
	protected TranscodeFile buildTranscodeFile( MovieAndShowInfo theMovieAndShowInfo, CorrelatedFile theCorrelatedFile )
	{
		log.info( "Processing movie or show: " + theMovieAndShowInfo.getMovieOrShowName() + ", file: " + theCorrelatedFile.toString() ) ;

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
		return theTranscodeFile ;
	}

	/**
	 * Determine if the file needs to be remuxed or retranscoded, or neither.
	 * @param theTranscodeFile
	 * @return:
	 * 	0 if this file needs neither a remux or retranscode.
	 *  1 if this file needs a remux.
	 *  2 if this file needs a retranscode.
	 */
	protected int needRemuxOrTranscode( final TranscodeFile theTranscodeFile, final File oldMP4File )
	{
		boolean needRemux = false ;
		boolean needTranscode = false ;

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
		int retMe = 0 ; // nothing needed
		if( needTranscode )
		{
			retMe = 2 ;
		}
		if( needRemux )
		{
			retMe = 1 ;
		}
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
	public boolean processCorrelatedFile( MovieAndShowInfo theMovieAndShowInfo, CorrelatedFile theCorrelatedFile )
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

	protected boolean transcodeFile( MovieAndShowInfo theMovieAndShowInfo,
			CorrelatedFile theCorrelatedFile,
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

	protected boolean remuxFile( MovieAndShowInfo theMovieAndShowInfo,
			CorrelatedFile theCorrelatedFile,
			TranscodeFile fileToTranscode )
	{
		TranscodeCommon tCommon = new TranscodeCommon(
				log,
				common,
				fileToTranscode.getMKVInputDirectory(),
				fileToTranscode.getMKVFinalDirectory(),
				fileToTranscode.getMP4OutputDirectory(),
				fileToTranscode.getMP4FinalDirectory() ) ;
		boolean remuxSucceeded = tCommon.remuxFile( fileToTranscode ) ;
		return remuxSucceeded ;
	}

	public boolean atLeastOneThreadIsAlive( final List< RemuxWithSubtitles > theThreads )
	{
		if( !isUseThreads() )
		{
			// Not using threads, so just return true
			return true ;
		}

		for( RemuxWithSubtitles theThread : theThreads )
		{
			if( theThread.isAlive() )
			{
				return true ;
			}
		}
		return false ;
	}

	public Object getDriveLock( final String theDrive )
	{
		Object theLock = driveLocks.get( theDrive ) ;
		if( null == theLock )
		{
			log.warning( "Unable to find lock for drive: " + theDrive ) ;
			theLock = new String( "Broken Lock" ) ;
		}
		return theLock ;
	}

	protected String getDriveToRemux()
	{
		return driveToRemux;
	}

	public String getMP4OutputDirectory()
	{
		return mp4OutputDirectory;
	}

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

	protected void setDriveToRemux( String driveToRemux )
	{
		this.driveToRemux = driveToRemux;
	}

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
