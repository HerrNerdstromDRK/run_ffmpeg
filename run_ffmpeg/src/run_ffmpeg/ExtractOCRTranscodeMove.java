package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;

import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

/**
 * The purpose of this class is to run the full workflow on a file or folder.
 * It will extract SRT/PGS, OCR if necessary, transcode, and move the file(s)
 *  to their destination.
 * @author Dan
 */
public class ExtractOCRTranscodeMove extends Thread
{
	/// Setup the logging subsystem
	private transient Logger log = null ;

	/// Hook in the Common methods and values
	private transient Common common = null ;

	/// File name to which to log activities for this application.
	private final String logFileName = "log_extract_and_ocr.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private final String stopFileName = "C:\\Temp\\stop_extract_and_ocr.txt" ;
	
	/// Handle to the mongodb
	private transient MoviesAndShowsMongoDB masMDB = null ;
	
	/// Handle to the MovieAndShowInfo collection
	private transient MongoCollection< MovieAndShowInfo > movieAndShowInfoCollection = null ;
	
	public ExtractOCRTranscodeMove()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		// Establish connection to the database.
		masMDB = new MoviesAndShowsMongoDB() ;
		movieAndShowInfoCollection = masMDB.getMovieAndShowInfoCollection() ;
	}
	
	/**
	 * Transcode all files in each of the listed folders.
	 * @param folderList
	 */
	public void runFolders( List< String > folderList )
	{		
		for( String folder : folderList )
		{
			final String mkvInputDirectory = folder ;
			final String mkvFinalDirectory = makeFinalMKVDirectory( mkvInputDirectory ) ;
			final String mp4OutputDirectory = TranscodeCommon.getDefaultMP4OutputDirectory() ;
			final String mp4FinalOutputDirectory = makeFinalMP4Directory( mkvInputDirectory ) ;
			TranscodeFile fileToTranscode = new TranscodeFile(
					folder,
					
					)
					
					
					public TranscodeFile( final File theMKVFile,
							final String mkvFinalDirectory,
							final String mp4OutputDirectory,
							final String mp4FinalDirectory,
							Logger log,
							TranscodeCommon transcodeCommon )
		}
	}
	
	protected String makeFinalMP4Directory( final String mkvInputDirectory )
	{
		
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
	protected String makeFinalMKVDirectory( final String mkvInputDirectory )
	{
		if( !mkvInputDirectory.contains( "To Convert" ) )
		{
			// output == input
			return mkvInputDirectory ;
		}
		// Post condition: The given folder is on the To Convert path.
		if( mkvInputDirectory.contains( "TV Show" ) )
		{
			return mkvInputDirectory.replace( "To Convert - TV Shows", "TV Shows" ) ;
		}
		return mkvInputDirectory.replace( "To Convert", "Movies" ) ;
	}
	
	public void runOneFile( TranscodeFile fileToTranscode )
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

		// Transcode the file.
		// Force a refresh of supporting files (.srt) by creating a new TranscodeFile
		fileToTranscode.buildSubTitleFileList() ;
		FFmpegProbeResult mkvProbeResult = common.ffprobeFile( new File( fileToTranscode.getMKVInputFileNameWithPath() ), log ) ;
		if( null == mkvProbeResult )
		{
			log.warning( "mkvProbeResult is null" ) ;
		}
		fileToTranscode.processFFmpegProbeResult( mkvProbeResult ) ;
		fileToTranscode.makeDirectories() ;
		fileToTranscode.setTranscodeInProgress();

		TranscodeCommon tCommon = new TranscodeCommon(
				log,
				common,
				fileToTranscode.getMKVInputDirectory(),
				fileToTranscode.getMKVFinalDirectory(),
				fileToTranscode.getMP4OutputDirectory(),
				fileToTranscode.getMP4FinalDirectory() ) ;
		boolean transcodeSucceeded = tCommon.transcodeFile( fileToTranscode ) ;
		if( !transcodeSucceeded )
		{
			log.warning( "Transcode failed" ) ;
			fileToTranscode.unSetTranscodeInProgress() ;
			return ;
		}
		fileToTranscode.setTranscodeComplete() ;

		// Move files to their destinations
		// For now, only the mp4 file needs to move.
		log.info( "Moving mp4 file from " + fileToTranscode.getMP4OutputDirectory() + " to " + fileToTranscode.getMP4FinalDirectory() ) ;
		if( !common.getTestMode() )
		{
			final String mp4FileNameInWorkingDirectory = fileToTranscode.getMP4OutputFileNameWithPath() ;
			final String mp4FileNameInFinalDirectory = fileToTranscode.getMP4FinalFileNameWithPath() ;

			try
			{
				Path temp = Files.move(
						Paths.get( mp4FileNameInWorkingDirectory ),
						Paths.get( mp4FileNameInFinalDirectory ) ) ;
				if( temp != null )
				{
					log.info( "Move successful." ) ;
				}
				else
				{
					log.warning( "Move failed." ) ;
				}
			}
			catch( Exception theException )
			{
				log.warning( "Exception: " + theException.toString() ) ;
				return ;
			}
		}

		// Update the probe information for this file
		// Be sure to force the refresh.
		ProbeDirectories pd = new ProbeDirectories() ;
		FFmpegProbeResult mp4ProbeResult = pd.probeFileAndUpdateDB( new File( fileToTranscode.getMP4FinalFileNameWithPath() ), true ) ;

		// Update the movie and show index, creating it if not already present
		Bson movieAndShowInfoIDFilter = Filters.eq( "mkvLongPath", fileToTranscode.getMKVFinalDirectory() ) ;
		MovieAndShowInfo movieAndShowInfo = movieAndShowInfoCollection.find( movieAndShowInfoIDFilter ).first() ;
		if( null == movieAndShowInfo )
		{
			// No information found about this movie or show.
			// Create it.
			movieAndShowInfo = new MovieAndShowInfo( mp4ProbeResult, log ) ;
			
			// At this point, the transcode is successful.
			movieAndShowInfo.addMKVFile( mkvProbeResult ) ;
			movieAndShowInfoCollection.insertOne( movieAndShowInfo ) ;
		}
		movieAndShowInfo.updateCorrelatedFile( mp4ProbeResult ) ;
		movieAndShowInfo.makeReadyCorrelatedFilesList() ;
		movieAndShowInfoCollection.replaceOne( movieAndShowInfoIDFilter,  movieAndShowInfo ) ;
	}
	
	public static void main(String[] args)
	{

	}

}
