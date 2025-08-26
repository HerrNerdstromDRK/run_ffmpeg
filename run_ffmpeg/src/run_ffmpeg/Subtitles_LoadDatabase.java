package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.mongodb.client.MongoCollection;

/**
 * This class will walk through the list of directories/files presented in execute() and load all .sup files into the OCR
 *  collection and all video files into the extract collection for processing via the Workflow.
 */
public class Subtitles_LoadDatabase
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;

	protected transient MoviesAndShowsMongoDB masMDB = null ;
	protected transient MongoCollection< JobRecord_FileNameWithPath > extractSubtitleCollection = null ;
	protected transient MongoCollection< JobRecord_FileNameWithPath > createSRTWithOCRCollection = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_subtitles_loaddatabase.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	//	private static final String stopFileName = "C:\\Temp\\stop_subtitles_loaddatabase.txt" ;

	public Subtitles_LoadDatabase()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;

		initObject() ;
	}

	public Subtitles_LoadDatabase( Logger log, Common common )
	{
		this.log = log ;
		this.common = common ;

		initObject() ;
	}

	private void initObject()
	{
		masMDB = new MoviesAndShowsMongoDB( log ) ;

		extractSubtitleCollection = masMDB.getAction_ExtractSubtitleCollection() ;
		log.info( "Extract subtitle database has " + extractSubtitleCollection.countDocuments() + " object(s) currently loaded." ) ;

		createSRTWithOCRCollection = masMDB.getAction_CreateSRTsWithOCRCollection() ;
		log.info( "OCR database has " + createSRTWithOCRCollection.countDocuments() + " object(s) currently loaded." ) ;
	}

	public static void main( final String[] args )
	{
		(new Subtitles_LoadDatabase()).execute() ;
	}

	public void execute()
	{
		// Build the list of folders to extract and process (OCR/transcribe)
		List< String > foldersToExtractAndConvert = new ArrayList< String >() ;
		//		foldersToExtractAndConvert.add( Common.getAllMediaFolders() ) ;
		foldersToExtractAndConvert.add( Common.getPathToTVShows() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToMovies() ) ;
		foldersToExtractAndConvert.add( Common.getPathToOCR() ) ;
//		foldersToExtractAndConvert.add( Common.getPathToStaging() ) ;
		//		foldersToExtractAndConvert.add( "\\\\skywalker\\\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}\\Season 02" ) ;

		log.info( "Extracting subtitles in " + foldersToExtractAndConvert.toString() ) ;

		// Locate and add any .sup files to the database for OCR.
		{
			log.info( "Searching for .sup files..." ) ;
			final String[] imageFormatExtensions = { "sup" } ;
			final List< File > supInputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, imageFormatExtensions ) ;

			addToDatabase_OCR( supInputFiles ) ;

			log.info( "Added " + supInputFiles.size() + " .sup file(s) to database" ) ;
		}

		// Find and load video files (mkv/mp4/etc.) into the database to extract.
		{
			log.info( "Searching for video files to extract..." ) ;
			final List< File > inputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, Common.getVideoExtensions() ) ;
			log.info( "Found " + inputFiles.size() + " file(s) from which to extract subtitles" ) ;

			// This will add the files to the database regardless if .srt files exist for them.
			// This is on purpose to simplify code here -- the file check code must live somewhere, so I choose to keep this
			//  code cleaner/smaller...for no particular reason.
			addToDatabase_Extract( inputFiles ) ;
			log.info( "Number of files to extract: " + extractSubtitleCollection.countDocuments() ) ;
		}

		log.info( "Shutdown." ) ;
	}

	public void addToDatabase_Extract( final File inputFile )
	{
		assert( inputFile != null ) ;

		extractSubtitleCollection.insertOne( new JobRecord_FileNameWithPath( inputFile.getAbsolutePath() ) ) ;
	}

	public void addToDatabase_Extract( final List< File > inputFiles )
	{
		assert( inputFiles != null ) ;

		// Build a JobRecord for each and then add all at once.
		List< JobRecord_FileNameWithPath > jobRecords = new ArrayList< JobRecord_FileNameWithPath >() ;
		inputFiles.forEach( file -> jobRecords.add( new JobRecord_FileNameWithPath( file ) ) ) ;

		if( !jobRecords.isEmpty() )
		{
			extractSubtitleCollection.insertMany( jobRecords ) ;
		}
	}

	public void addToDatabase_OCR( final File fileToOCR )
	{
		assert( fileToOCR != null ) ;

		createSRTWithOCRCollection.insertOne( new JobRecord_FileNameWithPath( fileToOCR ) ) ;
	}

	public void addToDatabase_OCR( final List< File > filesToOCR )
	{
		assert( filesToOCR != null ) ;

		// Build a JobRecord for each and then add all at once.
		List< JobRecord_FileNameWithPath > jobRecords = new ArrayList< JobRecord_FileNameWithPath >() ;
		filesToOCR.forEach( file -> jobRecords.add( new JobRecord_FileNameWithPath( file ) ) ) ;

		if( !jobRecords.isEmpty() )
		{
			createSRTWithOCRCollection.insertMany( jobRecords ) ;
		}
	}

}
