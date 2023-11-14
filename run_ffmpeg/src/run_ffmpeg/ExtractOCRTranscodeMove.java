package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * The purpose of this class is to run the full workflow on a file or folder.
 * It will extract SRT/PGS, OCR if necessary, transcode, and move the file(s)
 *  to their destination.
 * External use of this class should finish by calling waitForThreadsToComplete().
 * @author Dan
 */
public class ExtractOCRTranscodeMove extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_ocr_transcode_move.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_extract_ocr_transcode_move.txt" ;

	/// Move file thread controller.
	MoveFiles moveFiles = null ;

	/// Handle to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	/// Handle to the probe info collection to lookup and store probe information for the mkv
	/// and mp4 files.
//	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;
	
	/// Sort and transcode files from smallest to largest.
	/// If false, then sort largest to smallest.
	private boolean sortSmallToLarge = true ;

	public ExtractOCRTranscodeMove()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		// Create the MoveFiles handler, which will also start the threads.
		Logger moveFilesLogger = Common.setupLogger( "MoveFiles", MoveFiles.getLogFileName() ) ;
		moveFiles = new MoveFiles( moveFilesLogger, common ) ;

		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
//		probeInfoCollection = masMDB.getProbeInfoCollection() ;
	}

	public String getStopFileName()
	{
		return stopFileName ;
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
			final String tvShowSeasonName = mkvInputDirectoryFile.getName() ;
			final String tvShowName = mkvInputDirectoryFile.getParentFile().getName() ;
			mp4FinalDirectory += common.getPathSeparator()
					+ "TV Shows"
					+ common.getPathSeparator()
					+ tvShowName
					+ common.getPathSeparator()
					+ tvShowSeasonName ;
		}
		return mp4FinalDirectory ;
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
		}
		else if( mkvInputDirectory.contains( "To Convert" ) )
		{
			mkvFinalDirectory = mkvInputDirectory.replace( "To Convert", "Movies" ) ;
		}
		else if( common.isDoMoveMKVFiles() )
		{
			mkvFinalDirectory = common.addPathSeparatorIfNecessary( common.getMKVDriveWithMostAvailableSpace() ) ;
			if( mkvInputDirectory.contains( "Season " ) )
			{
				// TV Show
				// mkvInputFile will be of the form:
				// C:\\Temp\\Show Name\\Season 01\\Show Name - S01E01 - Episode Name.mkv
				final String tvShowName = mkvInputDirectoryFile.getParentFile().getName() ;
				final String tvShowSeasonName = mkvInputFile.getParentFile().getName() ;
				mkvFinalDirectory += "TV Shows"
						+ common.getPathSeparator()
						+ tvShowName
						+ common.getPathSeparator()
						+ tvShowSeasonName ;
			}
			else
			{
				// Movie
				// mkvInputFile will be of the form:
				// C:\\Temp\\Movie Name (2000)\\File Name-behindthescenes.mkv
				final String movieName = 
				mkvFinalDirectory += "Movies"
						+ common.getPathSeparator()
						+ mkvInputFile.getParentFile().getName() ;
			}
		}
		else
		{
			// Leave the mkv file where it is.
			// Included here for logic transparency
			// mkvFinalDirectory = mkvInputDirectory ;
		}

		return mkvFinalDirectory ;
	}

	public static void main(String[] args)
	{
		ExtractOCRTranscodeMove eotm = new ExtractOCRTranscodeMove() ;
		eotm.runFolders() ;
		eotm.waitForThreadsToComplete() ;
		System.out.println( "ExtractOCRTranscodeMode.main> Shutdown." ) ;
	}

	/**
	 * Transcode all folders with To Convert subdirectories.
	 */
	public void runFolders()
	{
		common.setTestMode( true ) ;
		common.setDoMoveMKVFiles( true ) ;
		List< String > foldersToTranscode = new ArrayList< String >() ;
//		foldersToTranscode.add( "\\\\yoda\\MKV_Archive10\\To Convert - TV Shows" ) ;
		foldersToTranscode.add( "C:\\Temp") ;
//		foldersToTranscode.add( "C:\\Temp\\TV Show") ;
		
//		foldersToTranscode = common.addToConvertToEachDrive( common.getAllMKVDrives() ) ;
		
		runFolders( foldersToTranscode ) ;
	}

	/**
	 * Transcode all files in each of the listed folders. This first method will setup the directories
	 * and other information to inform the transcode, then call the runOneFile() method to execute the
	 * PGS extract, OCR, transcode, move, and db update.
	 * @param folderList
	 */
	public void runFolders( List< String > folderList )
	{		
		List< File > filesToTranscode = new ArrayList< File >() ;

		for( String inputDirectory : folderList )
		{
			final File folderToTranscodeFile = new File( inputDirectory ) ;
			if( !folderToTranscodeFile.exists() )
			{
				continue ;
			}
			// Post condition: folderToTranscode exists.

			// Retrieve the mkv input files in this folder.
			filesToTranscode.addAll( common.getFilesInDirectoryByExtension( inputDirectory,
					TranscodeCommon.getTranscodeExtensions() ) ) ;
		} // for( inputDirectory : folderList )
		log.info( "Transcoding " + filesToTranscode.size() + " file(s)" ) ;

		// Sort by size
		sortFilesToTranscode( filesToTranscode ) ;
		
		// Iterate through the input mkv files and transcode each
		for( File mkvInputFile : filesToTranscode )
		{
			if( !shouldKeepRunning() )
			{
				break ;
			}

			// Find or create the FFmpegProbeResult for this file.
			ProbeDirectories pd = new ProbeDirectories() ;
			FFmpegProbeResult mkvProbeResult = pd.probeFileAndUpdateDB( mkvInputFile ) ;
			//				Bson probeInfoFilter = Filters.eq( "fileNameWithPath", mkvInputFile.getAbsolutePath() ) ;
			//				FFmpegProbeResult mkvProbeResult = probeInfoCollection.find( probeInfoFilter ).first() ;
			//				if( null == mkvProbeResult )
			//				{
			//					mkvProbeResult = common.ffprobeFile( mkvInputFile, log ) ;
			//					if( null == mkvProbeResult )
			//					{
			//						// ffprobe failed.
			//						log.warning( "ffprobe failed for file: " + mkvInputFile.toString() ) ;
			//						continue ;
			//					}
			//					// Create a new probe result.
			//					// Store it where it is for now
			//					// TODO: If we move the mkvInputFile, then update the probe info also.
			//					probeInfoCollection.insertOne( mkvProbeResult ) ;
			//				}
			// Post condition: mkvProbeResult is non-null, and hopefully relevant.

			// Check for the situation where we are transcoding a file that is already part of 
			// the destination mkv directory.
			// Need to lookup the movie or show based on the first class content of the movie/show name
			// since we may be transcoding something here that is already in the database with its
			// final locations included.
			// Use the MovieAndShowInfo parser to extract all of the relevant information.
			MovieAndShowInfo testMovieAndShowInfo = new MovieAndShowInfo( mkvProbeResult, log ) ;

			// Use the testMovieAndShowInfo to lookup the correct MovieAndShowInfo by movieOrShowName
			Bson movieAndShowInfoIDFilter = Filters.eq( "movieOrShowName", testMovieAndShowInfo.getMovieOrShowName() ) ;
			MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;
			String mkvFinalDirectory = null ;
			String mp4FinalDirectory = null ;

			// Build the final mkv and mp4 directories.
			// First, check if the movieAndShowInfo is not null
			if( movieAndShowInfo != null )
			{
				// Found the movie or show in the database.
				// If the mkv/mp4 long path is invalid, then create a new one
				if( movieAndShowInfo.getMKVLongPath().equals( Common.getMissingFileSubstituteName() ) )
				{
					// Has an unusable mkv long path
					mkvFinalDirectory = makeFinalMKVDirectory( mkvProbeResult, testMovieAndShowInfo ) ;
				}
				else
				{
					// Valid mkv long path
					mkvFinalDirectory = movieAndShowInfo.getMKVLongPath() ;
				}
				
				// Do the same for the mp4 path
				if( movieAndShowInfo.getMP4LongPath().equals( Common.getMissingFileSubstituteName() ) )
				{
					// Has an unusable mp4 long path
					mp4FinalDirectory = makeFinalMP4Directory( mkvProbeResult, testMovieAndShowInfo ) ;
				}
				else
				{
					// Valid mp4 long path
					mp4FinalDirectory = movieAndShowInfo.getMP4LongPath() ;
				}
			}
			else
			{
				// movie or show not found in the database.
				// Build the directories here based on what data is available.
				mkvFinalDirectory = makeFinalMKVDirectory( mkvProbeResult, testMovieAndShowInfo ) ;
				mp4FinalDirectory = makeFinalMP4Directory( mkvProbeResult, testMovieAndShowInfo ) ;
			}

			// mp4OutputDirectory is the location where we will store the transcoded
			// mp4 output files before moving to their end destination (if different from output directory).
			final String mp4OutputDirectory = TranscodeCommon.getDefaultMP4OutputDirectory() ;

			// The TranscodeFile is used to store all of the relevant information to execute
			// a transcode.
			TranscodeFile fileToTranscode = new TranscodeFile(
					mkvInputFile,
					mkvFinalDirectory,
					mp4OutputDirectory,
					mp4FinalDirectory,
					log ) ;

			// Add the FFmpegProbeResult to the TranscodeFile as well.
			fileToTranscode.processFFmpegProbeResult( mkvProbeResult ) ;

			// Execute the Extract, OCR, transcode, move, and db update.
			runOneFile( fileToTranscode, mkvProbeResult ) ;
		} // for( File mkvInputFile : filesToTranscode )
	}

	/**
	 * Transcode this file. The second argument is included here just to ensure the file
	 *  has been probed. It should already have been processed by the TranscodeFile.
	 * @param fileToTranscode
	 */
	public void runOneFile( TranscodeFile fileToTranscode, FFmpegProbeResult mkvProbeResult )
	{
		if( fileToTranscode.isTranscodeComplete() || fileToTranscode.isTranscodeInProgress() )
		{
			// No need to re-accomplish
			return ;
		}
		
		// Extract subtitle streams.
		ExtractPGSFromMKVs extractPGSFromMKVs = new ExtractPGSFromMKVs() ;
		// runOneFile() will also prune the .sup files
		extractPGSFromMKVs.runOneFile( fileToTranscode ) ;

		// OCR the file, if necessary.
		// Note that the default behavior is to OCR all files in the immediate folder, which is
		//  fine with me.
		// TODO: Make this multi-threaded.
		OCRSubtitle ocrSubtitle = new OCRSubtitle() ;
		List< File > filesToOCR = common.getFilesInDirectoryByExtension( fileToTranscode.getMKVInputDirectory(),
				OCRSubtitle.getExtensionsToOCR() ) ;
		log.info( "filesToOCR: " + filesToOCR.toString() ) ;
		for( File fileToOCR : filesToOCR )
		{
			ocrSubtitle.doOCRFile( fileToOCR ) ;
			if( !common.getTestMode() )
			{
				fileToOCR.delete() ;
			}
		}

		// Force a refresh of supporting files (.srt).
		fileToTranscode.buildSubTitleFileLists() ;

		// Make the transcode, mkv final, and mp4 final directories, if necessary.
		fileToTranscode.makeDirectories() ;

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
		if( !transcodeSucceeded )
		{
			log.warning( "Transcode failed" ) ;
			fileToTranscode.unSetTranscodeInProgress() ;
			return ;
		}
		// Post-condition: transcode succeeded.
		fileToTranscode.setTranscodeComplete() ;

		// Move files to their destinations
		log.info( "Moving " + fileToTranscode.getMP4FileName() + " from " + fileToTranscode.getMP4OutputDirectory()
		+ " to " + fileToTranscode.getMP4FinalDirectory() ) ;
		moveFiles.addMP4FileMove( fileToTranscode.getMP4OutputFileNameWithPath(),
				fileToTranscode.getMP4FinalFileNameWithPath() ) ;

		log.info( "Moving " + fileToTranscode.getMKVFileName() + " from " + fileToTranscode.getMKVInputFileNameWithPath()
		+ " to " + fileToTranscode.getMKVFinalFileNameWithPath() ) ;
		moveFiles.addMKVFileMove( fileToTranscode.getMKVInputFileNameWithPath(),
				fileToTranscode.getMKVFinalFileNameWithPath() ) ;

		// Move the srt files also.
		Iterator< File > srtFileIterator = fileToTranscode.getAllSRTFilesIterator() ;
		while( srtFileIterator.hasNext() )
		{
			File sourceSRTFile = srtFileIterator.next() ;
			final String sourceSRTFileName = sourceSRTFile.getName() ;
			final String destinationSRTFileNameWithPath = fileToTranscode.getMKVFinalDirectory()
					+ common.getPathSeparator()
					+ sourceSRTFileName ;
			moveFiles.addMKVFileMove( sourceSRTFile.getAbsolutePath(), destinationSRTFileNameWithPath ) ;
		}

		// Can't do an actual probe on either final file here because they are currently being moved.
		// Update the existing mkvProbeResult with new file locations and build a dummy probe results
		// the mp4 file in its final directories.
		// Will use this to update the MovieAndShowInfo database.
		mkvProbeResult.setFileNameWithPath( fileToTranscode.getMKVFinalFileNameWithPath() ) ;
		mkvProbeResult.setFileNameWithoutPath( fileToTranscode.getMKVFileName() ) ;
		mkvProbeResult.setFileNameShort( Common.shortenFileName( fileToTranscode.getMKVFinalFileNameWithPath() ) ) ;

		FFmpegProbeResult mp4ProbeResult = new FFmpegProbeResult() ;
		mp4ProbeResult.setFileNameWithPath( fileToTranscode.getMP4FinalFileNameWithPath() ) ;
		mp4ProbeResult.setFileNameWithoutPath( fileToTranscode.getMP4FileName() ) ;
		mp4ProbeResult.setFileNameShort( Common.shortenFileName( fileToTranscode.getMP4FinalFileNameWithPath() ) ) ;
		mp4ProbeResult.probeTime = System.currentTimeMillis() + 1 ;
		mp4ProbeResult.chapters = new ArrayList< FFmpegChapter >() ;
		mp4ProbeResult.error = new FFmpegError() ;
		mp4ProbeResult.format = new FFmpegFormat() ;
		mp4ProbeResult.streams = new ArrayList< FFmpegStream >() ;

		// Update the movie and show index, creating it if not already present
		// Use the MovieAndShowInfo constructor to create the movieAndShowName
		MovieAndShowInfo tempMovieAndShowInfo = new MovieAndShowInfo( mkvProbeResult, log ) ;

		// Check to see if the movieAndshowInfo exists in the database already
		Bson movieAndShowInfoIDFilter = Filters.eq( "movieOrShowName", tempMovieAndShowInfo.getMovieOrShowName() ) ;
		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;
		if( null == movieAndShowInfo )
		{
			// No information found about this movie or show.
			// Create it.
			movieAndShowInfo = new MovieAndShowInfo( tempMovieAndShowInfo.getMovieOrShowName(), log) ;

			// Set the target mkv and mp4 final directories. This will fool the database
			// into accepting a bogus location until the files complete moving, at which point
			// this entry will be correct.
			movieAndShowInfo.setMKVLongPath( fileToTranscode.getMKVFinalDirectory() ) ;
			movieAndShowInfo.setMP4LongPath( fileToTranscode.getMP4FinalDirectory() ) ;

			// At this point, the transcode is successful.
			movieAndShowInfo.addMKVFile( mkvProbeResult ) ;
			movieAndShowInfo.addMP4File( mp4ProbeResult ) ;
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			movieAndShowInfoCollection.insertOne( movieAndShowInfo ) ;
		}
		else
		{
			movieAndShowInfo.addMP4File( mp4ProbeResult ) ;
			movieAndShowInfo.addMKVFile( mkvProbeResult ) ;
//			movieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter,  movieAndShowInfo ) ;
		}
	}

	public boolean shouldKeepRunning()
	{
		return !common.shouldStopExecution( getStopFileName() ) ;
	}
	
	public void sortFilesToTranscode( List< File > inputList )
	{
		if( sortSmallToLarge )
		{
			Collections.sort( inputList, new FileSortSmallToLarge() ) ;
		}
		else
		{
			Collections.sort( inputList, new FileSortLargeToSmall() ) ;
		}
	}

	public void waitForThreadsToComplete()
	{
		log.info( "Waiting for move files threads to complete..." ) ;
		moveFiles.waitForThreadsToComplete() ;
		log.info( "Move files threads shutdown." ) ;		
	}
}
