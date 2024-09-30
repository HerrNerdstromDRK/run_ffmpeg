package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class TranscodeAndMoveFiles extends run_ffmpegControllerThreadTemplate< TranscodeAndMoveFilesWorkerThread >
{
	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_transcode_and_move_files.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_transcode_and_move_files.txt" ;

	/// The list of files to transcode
	private List< TranscodeAndMoveFileInfo > filesToTranscode = new ArrayList< TranscodeAndMoveFileInfo >() ;

	/// The controller object to manage moving files.
	private MoveFiles moveFilesController = null ;

	/// Reference to the transcode worker thread.
	private TranscodeAndMoveFilesWorkerThread transcodeThread = null ;

	/// The output directory in which to store mp4 files before being moved to their final home.
	private String mp4OutputDirectory = null ;

	/// Set to true to sort files small to large. Otherwise, they will be sorted large to small.
	private boolean sortSmallToLarge = true ;

	public TranscodeAndMoveFiles()
	{
		super( logFileName, stopFileName ) ;
		initObject() ;
	}

	private void initObject()
	{
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
		moveFilesController = new MoveFiles( log, common ) ;
		mp4OutputDirectory = common.getPathToDefaultMP4OutputDirectory() ;
	}

	public static void main( String[] args )
	{
		TranscodeAndMoveFiles tamf = new TranscodeAndMoveFiles() ;
		tamf.Init() ;
		tamf.Execute() ;
		System.out.println( "Process shut down." ) ;
	}

	@Override
	public void Execute_afterEndMainLoop()
	{
		moveFilesController.stopRunning() ;
	}

	/**
	 * Initialize this object.
	 */
	@Override
	public void Init()
	{
		// This object only works with threads enabled.
		setUseThreads( true ) ;
		common.setTestMode( false ) ;
		common.setDoMoveFiles( true ) ;
		setSortSmallToLarge( true ) ;

		// Populate the list of folders to transcode.
		List< String > foldersToTranscode = new ArrayList< String >() ;

//		foldersToTranscode.addAll( common.addMoviesAndTVShowFoldersToEachDrive( common.getAllMKVDrives() ) ) ;
//		foldersToTranscode.add( "\\\\yoda\\\\MKV_Archive10\\To Convert\\Harold And Kumar Escape From Guantanamo Bay (2008)" ) ;
		foldersToTranscode.addAll( common.addToConvertToEachDrive( common.getAllMKVDrives() ) ) ;
//		foldersToTranscode.add( "E:\\\\The Sopranos" ) ;
	
		log.info( "Will transcode these folders: " + foldersToTranscode.toString() ) ;

		// Search each folder for files that can be transcoded
		List< File > allFilesToTranscode = new ArrayList< File >() ;
		FindFiles findFiles = new FindFiles( log, common, masMDB, probeInfoCollection ) ;
		findFiles.addFoldersToSearch( foldersToTranscode ) ;
		findFiles.addExtensionsToFind( TranscodeCommon.getTranscodeExtensions() ) ;
		allFilesToTranscode.addAll( findFiles.getFiles() ) ;
		sortFilesToTranscode( allFilesToTranscode ) ;

		log.info( "Found " + allFilesToTranscode.size() + " file(s)" ) ;

		// Build a TranscodeFile for each file to transcode to make the code below simpler.
		// buildTranscodeFile() will check if the destination mp4 file already exists. If so, the method
		// will return null.
		for( File theFile : allFilesToTranscode )
		{
			// Since this can take a while to process, check if we should keep running.
			TranscodeAndMoveFileInfo newFileToTranscode = buildTranscodeFile( theFile ) ;
			if( newFileToTranscode != null )
			{
				// Some conditions, such as an existing mp4 file with newer time markings, will cause
				// the buildTranscodeFile() to fail. Do so gracefully here.
				filesToTranscode.add( newFileToTranscode ) ;
			}
		}

		//		log.info( "Will transcode these files: " ) ;
		//		for( TranscodeAndMoveFileInfo theFileToTranscode : filesToTranscode )
		//		{
		//			log.info( theFileToTranscode.toString() ) ;
		//		}
	}

	/**
	 * Build a transcode file for the given input mkv file. If the final mp4 file already exists then return null.
	 * @param folderToExtract
	 * @return The new transcode file or null if unsuccessful
	 */
	public TranscodeAndMoveFileInfo buildTranscodeFile( final File mkvFile )
	{
		assert( mkvFile != null ) ;
		log.fine( "building transcode file: " + mkvFile.getAbsolutePath() ) ;

		// Check for the situation where we are transcoding a file that is already part of 
		// the destination mkv directory or has an existing mp4 file.
		// Need to lookup the movie or show based on the first class content of the movie/show name
		// since we may be transcoding something here that is already in the database with its
		// final locations included.
		// Use the MovieAndShowInfo parser to extract all of the relevant information.
		MovieAndShowInfo testMovieAndShowInfo = new MovieAndShowInfo( mkvFile, log ) ;

		// Use the testMovieAndShowInfo to lookup the correct MovieAndShowInfo by movieOrShowName
		Bson movieAndShowInfoIDFilter = Filters.eq( "movieOrShowName", testMovieAndShowInfo.getMovieOrShowName() ) ;
		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;
		if( movieAndShowInfo != null )
		{
			// The movie or show already exists in the database.
			// Check if the target mp4 file also already exists.
			// TODO: Probably move some of this to MovieAndShowInfo -- I should be able to lookup/remove an mkv or mp4 file.
			final String mp4LongPath = movieAndShowInfo.getMP4LongPath() ;
			if( mp4LongPath != null )
			{
				// 
				final String mp4FileNameWithPath = common.addPathSeparatorIfNecessary( mp4LongPath )
					+ common.addPathSeparatorIfNecessary( Common.getSeasonString( mkvFile ) ) 
					+ mkvFile.getName().replace( ".mkv", ".mp4" ) ;
				final File mp4File = new File( mp4FileNameWithPath ) ;
				if( mp4File.exists() )
				{
					log.fine( "Found that mp4 file " + mp4File.getAbsolutePath() + " exists for mkvFile: " + mkvFile.getAbsolutePath() ) ;

					// An mp4 file correspond to the mkv file exists but is newer than the mkv file.
					// Note this but ignore it.
					// log.fine( "Found existing mp4 file for mkv file " + mkvFile.getAbsolutePath() + " but mp4 file is newer. Ignoring mkv file." ) ;
					return null ;
					//					}
				} // if( mp4File.exists() )
			} // if( mp4LongPath != null )
		} // if( movieAndShowInfo != null )
		
		// seasonString could be empty or a valid season string, but not null
		String seasonString = Common.getSeasonString( mkvFile ) ;
		
		// Populate the TranscodeAndMoveFileInfo with the TranscodeFile, mkv probe info, and MovieAndShowInfo
		TranscodeAndMoveFileInfo transcodeAndMoveFileInfo = new TranscodeAndMoveFileInfo() ;

		// Create the FFmpegProbeResult for this file.
		FFmpegProbeResult mkvProbeResult = common.ffprobeFile( mkvFile, log ) ; 
		if( null == mkvProbeResult )
		{
			log.warning( "Unable to create probe input file: " + mkvFile.getAbsolutePath() ) ;
			return null ;
		}
		// Post condition: mkvProbeResult is non-null, and hopefully relevant.
		// Since we don't yet know the final location of the mkv file and because the transcode could still fail
		// or be cancelled, do NOT insert the mkv probe information into the database.
		transcodeAndMoveFileInfo.mkvProbeInfo = mkvProbeResult ;

		String mkvFinalDirectory = null ;
		String mp4FinalDirectory = null ;

		// Build the final mkv and mp4 directories.
		// First, check if the movieAndShowInfo is not null
		if( movieAndShowInfo != null )
		{
			boolean changedMovieAndShowInfo = false ;
			// Found the movie or show in the database.
			// If the mkv/mp4 long path is invalid, then create a new one
			if( movieAndShowInfo.getMKVLongPath().isEmpty()
					|| movieAndShowInfo.getMKVLongPath().isBlank()
					|| movieAndShowInfo.getMKVLongPath().equals( Common.getMissingFileSubstituteName() ) )
			{
				// Has an unusable mkv long path
				mkvFinalDirectory = makeFinalMKVDirectory( mkvProbeResult, testMovieAndShowInfo ) ;
				movieAndShowInfo.setMKVLongPath( mkvFinalDirectory ) ;
				changedMovieAndShowInfo = true ;
			}
			else
			{
				// Valid mkv long path
				mkvFinalDirectory = movieAndShowInfo.getMKVLongPath() ;
				if( !seasonString.isBlank() )
				{
					mkvFinalDirectory = common.addPathSeparatorIfNecessary( mkvFinalDirectory ) + seasonString ;
				}
				
			}

			// Do the same for the mp4 path
			if( movieAndShowInfo.getMP4LongPath().isEmpty()
					|| movieAndShowInfo.getMP4LongPath().isBlank()
					|| movieAndShowInfo.getMP4LongPath().equals( Common.getMissingFileSubstituteName() ) )
			{
				// Has an unusable mp4 long path
				mp4FinalDirectory = makeFinalMP4Directory( mkvProbeResult, testMovieAndShowInfo ) ;
				movieAndShowInfo.setMP4LongPath( mp4FinalDirectory ) ;
				changedMovieAndShowInfo = true ;
			}
			else
			{
				// Valid mp4 long path
				mp4FinalDirectory = movieAndShowInfo.getMP4LongPath() ;
				if( !seasonString.isBlank() )
				{
					mp4FinalDirectory =  common.addPathSeparatorIfNecessary( mp4FinalDirectory ) + seasonString ;
				}
			}
			if( changedMovieAndShowInfo )
			{
				// Modified the MovieAndShowInfo object -- update the database to reflect.
				movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter, movieAndShowInfo ) ;
			}
		}
		else
		{
			// movie or show not found in the database.
			// Build the directories here based on what data is available.
			mkvFinalDirectory = makeFinalMKVDirectory( mkvProbeResult, testMovieAndShowInfo ) ;
			mp4FinalDirectory = makeFinalMP4Directory( mkvProbeResult, testMovieAndShowInfo ) ;

			// Need to create a new MovieAndShowInfo for this item.
			movieAndShowInfo = new MovieAndShowInfo( mkvProbeResult, log ) ;
			movieAndShowInfo.setMKVLongPath( mkvFinalDirectory ) ;
			movieAndShowInfo.setMP4LongPath( mp4FinalDirectory ) ;

			// Update the database with the show or movie information.
			movieAndShowInfoCollection.insertOne( movieAndShowInfo ) ;
		}
		// Save this data item for transcoding.
		transcodeAndMoveFileInfo.movieAndShowInfo = movieAndShowInfo ;

		// mp4OutputDirectory is the location where we will store the transcoded
		// mp4 output files before moving to their end destination (if different from output directory).
		final String mp4OutputDirectory = common.getPathToDefaultMP4OutputDirectory() ;

		// The TranscodeFile is used to store all of the relevant information to execute
		// a transcode.
		TranscodeFile fileToTranscode = new TranscodeFile(
				mkvFile,
				mkvFinalDirectory,
				mp4OutputDirectory,
				mp4FinalDirectory,
				log ) ;

		// Add the FFmpegProbeResult to the TranscodeFile as well.
		fileToTranscode.processFFmpegProbeResult( mkvProbeResult ) ;
		transcodeAndMoveFileInfo.fileToTranscode = fileToTranscode ;

		return transcodeAndMoveFileInfo ;
	}

	/**
	 * Build the worker threads for this instance. Since we can only transcode one file at a time, just
	 *  use a single thread. However, instantiate the MoveFiles controller wrapper so we have a way to
	 *  deactive the MoveFiles subsystem.
	 */
	@Override
	protected List< TranscodeAndMoveFilesWorkerThread > buildWorkerThreads()
	{
		List< TranscodeAndMoveFilesWorkerThread > threads = new ArrayList< TranscodeAndMoveFilesWorkerThread >() ;
		transcodeThread = new TranscodeAndMoveFilesWorkerThread(
				this,
				masMDB,
				log,
				common,
				filesToTranscode ) ;

		// Important that the name of the thread here be alphabetically earlier than the move files threads in
		// the event that this application is run in single threaded mode -- in that case, if a move files
		// thread runs first, it will just sit there forever.
		transcodeThread.setName( "DoTranscode" ) ;
		threads.add( transcodeThread ) ;

		MoveFilesControllerWrapper mfcw = new MoveFilesControllerWrapper( this, moveFilesController, log, common ) ;
		mfcw.setName( "MoveFilesControllerWrapper" ) ;
		threads.add( mfcw ) ;

		return threads ;
	}

	public String getMP4OutputDirectory()
	{
		return mp4OutputDirectory ;
	}

	public boolean isSortSmallToLarge()
	{
		return sortSmallToLarge ;
	}

	/**
	 * Return the folder path to which mkv files from the given mkvInputDirectory
	 *  should be permanently placed.
	 * If the input directory is of the form <drive>/<Movies,TV Shows>/<movie or show name> then leave in place.
	 * If the input directory is of the form <drive>/<To Convert,To Convert - TV Shows>/<movie or show name>, then the
	 *  output directory should be <drive>/<Movies,TV Shows>/<movie or show name>.
	 * Pre-condition: This method is only invoked if the movie or tv show is absent from the database.
	 * @param mkvInputDirectory
	 * @return
	 */
	protected String makeFinalMKVDirectory( final FFmpegProbeResult mkvProbeResult, MovieAndShowInfo testMovieAndShowInfo )
	{
		final File mkvInputFile = new File( mkvProbeResult.getFileNameWithPath() ) ;
		final String mkvInputDirectory = mkvInputFile.getParent() ;
		final File mkvInputDirectoryFile = new File( mkvInputDirectory ) ;
		String mkvFinalDirectory = mkvInputDirectory ;

		// Must account for:
		// - "To Convert" in the path
		// -- with and without "TV Shows"
		// - mkv file is in its final directory (moveMKVFiles will be false)

		if( mkvInputDirectory.contains( "To Convert - TV Shows" ) )
		{
			mkvFinalDirectory = mkvInputDirectory.replace( "To Convert - TV Shows", "TV Shows" ) ;
			final File mkvInputDirectoryWithSeasonName = new File( mkvFinalDirectory ) ;
			mkvFinalDirectory = mkvInputDirectoryWithSeasonName.getParentFile().getAbsolutePath() ;
			
			// Be sure to strip out the trailing "Season XX/"
//			final String tvShowName = mkvInputDirectoryFile.getParentFile().getName() ;
////			final String tvShowSeasonName = mkvInputFile.getParentFile().getName() ;
//			mkvFinalDirectory += "TV Shows"
//					+ common.getPathSeparator()
//					+ tvShowName ;
		}
		else if( mkvInputDirectory.contains( "To Convert" ) )
		{
			mkvFinalDirectory = mkvInputDirectory.replace( "To Convert", "Movies" ) ;
		}
		else
		{
			mkvFinalDirectory = common.addPathSeparatorIfNecessary( common.getMKVDriveWithMostAvailableSpace() ) ;
			if( mkvInputDirectory.contains( "Season " ) )
			{
				// TV Show
				// mkvInputFile will be of the form:
				// C:\\Temp\\Show Name\\Season 01\\Show Name - S01E01 - Episode Name.mkv
				final String tvShowName = mkvInputDirectoryFile.getParentFile().getName() ;
//				final String tvShowSeasonName = mkvInputFile.getParentFile().getName() ;
				mkvFinalDirectory += "TV Shows"
						+ common.getPathSeparator()
						+ tvShowName ;
			}
			else
			{
				// Movie
				// mkvInputFile will be of the form:
				// C:\\Temp\\Movie Name (2000)\\File Name-behindthescenes.mkv
				mkvFinalDirectory += "Movies"
						+ common.getPathSeparator()
						+ mkvInputFile.getParentFile().getName() ;
			}
		}

		return mkvFinalDirectory ;
	}

	/**
	 * Determine where to place the final mp4 files for the given mkvInputDirectory.
	 * If the show/movie corresponds to an existing folder, for example when adding a new
	 *  season to an existing show, use that existing directory.
	 * If the show or movie directory does not exist, create it new.
	 * testMovieAndShowInfo is provided here because its parser is able to extract some basics
	 *  such as whether or not the file is a tv show or movie.
	 * Pre-condition: This method is only invoked if the movie or tv show is absent from the database.
	 * @param mkvInputDirectory
	 * @return
	 */
	protected String makeFinalMP4Directory( final FFmpegProbeResult mkvProbeResult, MovieAndShowInfo testMovieAndShowInfo )
	{
		String mp4FinalDirectory = common.getMP4DriveWithMostAvailableSpace() ;
		final File mkvInputFile = new File( mkvProbeResult.getFileNameWithPath() ) ;
		//		final String mkvInputDirectory = mkvInputFile.getParent() ;
		final File mkvInputDirectoryFile = new File( mkvInputFile.getParentFile().getAbsolutePath() ) ;

		if( !testMovieAndShowInfo.isTVShow() )
		{
			// Movie
			// Create the mp4LongPath
			// mp4DriveWithMostSpaceAvailable will be of the form "\\yoda\\MP4"
			mp4FinalDirectory += common.getPathSeparator()
					+ "Movies"
					+ common.getPathSeparator()
					+ testMovieAndShowInfo.getMovieOrShowName() ;
		}
		else
		{
			// TV Show
//			final String tvShowSeasonName = mkvInputDirectoryFile.getName() ;
			final String tvShowName = mkvInputDirectoryFile.getParentFile().getName() ;
			mp4FinalDirectory += common.getPathSeparator()
					+ "TV Shows"
					+ common.getPathSeparator()
					+ tvShowName ;
		}
		return mp4FinalDirectory ;
	}

	public void moveFile( TranscodeFile fileToMove )
	{
		moveFilesController.queueFileToMove( fileToMove ) ;

		// Move the srt files also.
		Iterator< File > srtFileIterator = fileToMove.getAllSRTFilesIterator() ;
		while( srtFileIterator.hasNext() )
		{
			File sourceSRTFile = srtFileIterator.next() ;
			final String sourceSRTFileName = sourceSRTFile.getName() ;
			final String destinationSRTFileNameWithPath = fileToMove.getMKVFinalDirectory()
					+ common.getPathSeparator()
					+ sourceSRTFileName ;
			moveFilesController.queueFileToMove( sourceSRTFile.getAbsolutePath(), destinationSRTFileNameWithPath ) ;
		}
	}

	/**
	 * Return true if all move files threads queues are empty.
	 * @return
	 */
	protected boolean hasMoreWork()
	{
		return (!filesToTranscode.isEmpty() || transcodeThread.isAlive() || moveFilesController.hasMoreWork()) ;
	}

	public void setMP4OutputDirectory( String mp4OutputDirectory )
	{
		this.mp4OutputDirectory = mp4OutputDirectory ;
	}

	public void setSortSmallToLarge( boolean sortSmallToLarge )
	{
		this.sortSmallToLarge = sortSmallToLarge ;
	}

	/**
	 * Overload shouldKeepRunning() to watch for status of the queues. If the transcode or any move queue
	 * has work left, then return true. Return false otherwise.
	 */
	@Override
	public boolean shouldKeepRunning()
	{
		// Return true if any of the worker threads (transcode or move files) is doing something
		if( !hasMoreWork() )
		{
			return false ;
		}
		return super.shouldKeepRunning() ;
	}

	public void sortFilesToTranscode( List< File > inputList )
	{
		log.fine( "Before sort: " + inputList.toString() ) ;
		if( isSortSmallToLarge() )
		{
			Collections.sort( inputList, new FileSortSmallToLarge() ) ;
		}
		else
		{
			Collections.sort( inputList, new FileSortLargeToSmall() ) ;
		}
		log.fine( "After sort: " + inputList.toString() ) ;
	}
}
