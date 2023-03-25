package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private final String stopFileName = "C:\\Temp\\stop_extract_and_ocr.txt" ;

	/// Move file thread controller.
	MoveFiles moveFiles = null ;

	/// Handle to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;

	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;

	/// Handle to the probe info collection to lookup and store probe information for the mkv
	/// and mp4 files.
	private transient MongoCollection< FFmpegProbeResult > probeInfoCollection = null ;

	public ExtractOCRTranscodeMove()
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

	/**
	 * Transcode all folders with To Convert subdirectories.
	 */
	public void runFolders()
	{
		List< String > foldersToTranscode = common.addToConvertToEachDrive( common.getAllMKVDrives() ) ;
		runFolders( foldersToTranscode ) ;
	}

	/**
	 * Transcode all files in each of the listed folders.
	 * @param folderList
	 */
	public void runFolders( List< String > folderList )
	{		
		for( String inputDirectory : folderList )
		{
			final File folderToTranscodeFile = new File( inputDirectory ) ;
			if( !folderToTranscodeFile.exists() )
			{
				continue ;
			}
			// Post condition: folderToTranscode exists.

			// Retrieve the mkv input files in this folder.
			List< File > filesToTranscode = common.getFilesInDirectoryByExtension( inputDirectory,
					TranscodeCommon.getTranscodeExtensions() ) ;
			for( File mkvInputFile : filesToTranscode )
			{
				// Find or create the FFmpegProbeResult for this file.
				Bson probeInfoFilter = Filters.eq( "fileNameWithPath", mkvInputFile.getAbsolutePath() ) ;
				FFmpegProbeResult mkvProbeResult = probeInfoCollection.find( probeInfoFilter ).first() ;
				if( null == mkvProbeResult )
				{
					mkvProbeResult = common.ffprobeFile( mkvInputFile, log ) ;
					if( null == mkvProbeResult )
					{
						// ffprobe failed.
						log.warning( "ffprobe failed for file: " + mkvInputFile.toString() ) ;
						continue ;
					}
					// Create a new probe result.
					// Store it where it is for now
					// TODO: If we move the mkvInputFile, then update the probe info also.
					probeInfoCollection.insertOne( mkvProbeResult ) ;
				}
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

				if( movieAndShowInfo != null )
				{
					// Found the movie or show in the database.
					// Use those values for the directory build here.
					mkvFinalDirectory = movieAndShowInfo.getMKVLongPath() ;
					mp4FinalDirectory = movieAndShowInfo.getMP4LongPath() ;
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

				TranscodeFile fileToTranscode = new TranscodeFile(
						mkvInputFile,
						mkvFinalDirectory,
						mp4OutputDirectory,
						mp4FinalDirectory,
						log ) ;
				fileToTranscode.processFFmpegProbeResult( mkvProbeResult ) ;

				runOneFile( fileToTranscode, mkvProbeResult ) ;
			} // for( fileToTranscode )
		}
	}

	public void waitForThreadsToComplete()
	{
		log.info( "Waiting for move files threads to complete..." ) ;
		moveFiles.waitForThreadsToComplete() ;
		log.info( "Move files threads shutdown." ) ;		
	}

	/**
	 * Determine where to place the final mp4 files for the given mkvInputDirectory.
	 * If the show/movie corresponds to an existing folder, for example when adding a new
	 *  season to an existing show, use that existing directory.
	 * If the show or movie directory does not exist, create it new.
	 * testMovieAndShowInfo is provided here because its parser is able to extract some basics
	 *  such as whether or not the file is a tv show or movie.
	 * @param mkvInputDirectory
	 * @return
	 */
	protected String makeFinalMP4Directory( final FFmpegProbeResult mkvProbeResult, MovieAndShowInfo testMovieAndShowInfo )
	{
		String mp4FinalDirectory = common.getMP4DriveWithMostAvailableSpace() ;
		final File mkvInputFile = new File( mkvProbeResult.getFileNameWithPath() ) ;
		final String mkvInputDirectory = mkvInputFile.getParent() ;
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
			final String tvShowSeasonName = mkvInputDirectoryFile.getParentFile().getName() ;
			final String tvShowName = mkvInputDirectoryFile.getParentFile().getParentFile().getName() ;
			mp4FinalDirectory += "TV Shows"
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
	 * @param mkvInputDirectory
	 * @return
	 */
	protected String makeFinalMKVDirectory( final FFmpegProbeResult mkvProbeResult, MovieAndShowInfo testMovieAndShowInfo )
	{
		final File mkvInputFile = new File( mkvProbeResult.getFileNameWithPath() ) ;
		final String mkvInputDirectory = mkvInputFile.getParent() ;

		if( !mkvInputDirectory.contains( "To Convert" ) )
		{
			// Since the path doesn't include "To Convert" assume the file is in its
			// final destination.
			// output == input
			return mkvInputDirectory ;
		}
		// Post condition: The given folder is on the To Convert path.
		if( mkvInputDirectory.contains( "TV Shows" ) )
		{
			// It is a TV Show
			// Strip the "To Convert - " from the path
			return mkvInputDirectory.replace( "To Convert - TV Shows", "TV Shows" ) ;
		}
		// Otherwise, this is a movie. Remove the "To Convert"
		return mkvInputDirectory.replace( "To Convert", "Movies" ) ;
	}

	/**
	 * Transcode this file. The second argument is included here just to ensure that the file
	 *  has been probed. It should already have been processed by the TranscodeFile.
	 * @param fileToTranscode
	 */
	public void runOneFile( TranscodeFile fileToTranscode, FFmpegProbeResult inputFileProbeResult )
	{
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
		for( File fileToOCR : filesToOCR )
		{
			ocrSubtitle.doOCRFileName( fileToOCR.getAbsolutePath() ) ;
			if( !common.getTestMode() )
			{
				fileToOCR.delete() ;
			}
		}

		// Force a refresh of supporting files (.srt).
		fileToTranscode.buildSubTitleFileList() ;
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
		fileToTranscode.setTranscodeComplete() ;

		// Move files to their destinations
		log.info( "Moving MP4 file from " + fileToTranscode.getMP4OutputDirectory()
			+ " to " + fileToTranscode.getMP4FinalDirectory() ) ;
		moveFiles.addMP4FileMove( fileToTranscode.getMP4OutputFileNameWithPath(),
				fileToTranscode.getMP4FinalFileNameWithPath() ) ;

		log.info( "Moving MKV file from " + fileToTranscode.getMKVInputFileNameWithPath()
			+ " to " + fileToTranscode.getMKVFinalFileNameWithPath() ) ;
		moveFiles.addMKVFileMove( fileToTranscode.getMKVInputFileNameWithPath(),
				fileToTranscode.getMKVFinalFileNameWithPath() ) ;

		// Move the srt files also.
		Iterator< File > srtFileIterator = fileToTranscode.getSRTFileListIterator() ;
		while( srtFileIterator.hasNext() )
		{
			File sourceSRTFile = srtFileIterator.next() ;
			final String sourceSRTFileName = sourceSRTFile.getName() ;
			final String destinationSRTFileNameWithPath = fileToTranscode.getMKVFinalDirectory()
					+ common.getPathSeparator()
					+ sourceSRTFileName ;
			moveFiles.addMKVFileMove( sourceSRTFile.getAbsolutePath(), destinationSRTFileNameWithPath ) ;
		}
		
		// Update the probe information for this file
		ProbeDirectories pd = new ProbeDirectories() ;
		FFmpegProbeResult mp4ProbeResult = pd.probeFileAndUpdateDB( new File( fileToTranscode.getMP4FinalFileNameWithPath() ), true ) ;
		FFmpegProbeResult mkvProbeResult = pd.probeFileAndUpdateDB( new File( fileToTranscode.getMKVFinalFileNameWithPath() ), true ) ;

		// Update the movie and show index, creating it if not already present
		// Use the MovieAndShowInfo constructor to parse the input directory for the database search.
		MovieAndShowInfo movieAndShowInfo = new MovieAndShowInfo( mkvProbeResult, log ) ;
		Bson movieAndShowInfoIDFilter = Filters.eq( "movieOrShowName", movieAndShowInfo.getMovieOrShowName() ) ;
		movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;

		if( null == movieAndShowInfo )
		{
			// No information found about this movie or show.
			// Create it.
			movieAndShowInfo = new MovieAndShowInfo( mp4ProbeResult, log ) ;

			// At this point, the transcode is successful.
			movieAndShowInfo.addMKVFile( mkvProbeResult ) ;
			movieAndShowInfo.addMP4File( mp4ProbeResult ) ;
			movieAndShowInfo.makeReadyCorrelatedFilesList() ;
			movieAndShowInfoCollection.insertOne( movieAndShowInfo ) ;
		}
		movieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;
		movieAndShowInfo.makeReadyCorrelatedFilesList() ;
		movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter,  movieAndShowInfo ) ;
	}

	public static void main(String[] args)
	{
		ExtractOCRTranscodeMove eotm = new ExtractOCRTranscodeMove() ;
		eotm.runFolders() ;
		eotm.waitForThreadsToComplete() ;
	}

}
