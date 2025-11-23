package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
	protected transient MongoCollection< JobRecord_FileNameWithPath > createSRTWithTranscribeCollection = null ;

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

		createSRTWithTranscribeCollection = masMDB.getAction_CreateSRTsWithTranscribeCollection() ;
		log.info( "AI database has " + createSRTWithTranscribeCollection.countDocuments() + " object(s) currently loaded" ) ;		
	}

	public static void main( final String[] args )
	{
		(new Subtitles_LoadDatabase()).execute() ;
	}

	public void execute()
	{
		// Build the list of folders to extract and process (OCR/transcribe)
		List< String > foldersToExtractAndConvert = new ArrayList< String >() ;
		//foldersToExtractAndConvert.add( Common.getAllMediaFolders() ) ;
		foldersToExtractAndConvert.add( "S:\\Media\\To_OCR" ) ;
//		foldersToExtractAndConvert.add( Common.getPathToTVShows() ) ;
//		foldersToExtractAndConvert.add( Common.getPathToMovies() ) ;
//		foldersToExtractAndConvert.add( Common.getPathToOCR() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToStaging() ) ;
		//		foldersToExtractAndConvert.add( "C:\\Temp" ) ;
		//		foldersToExtractAndConvert.add( "\\\\skywalker\\Media\\TV_Shows\\A Pup Named Scooby-Doo (1988) {tvdb-73546}" ) ;

		log.info( "Extracting subtitles in " + foldersToExtractAndConvert.toString() ) ;

		// Find and load video files (mkv/mp4/etc.) into the database to extract.
		log.info( "Searching for video files to extract..." ) ;

		final List< File > videoFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, Common.getVideoExtensions() ) ;
		log.info( "Found " + videoFiles.size() + " video file(s) with extension " + Arrays.toString( Common.getVideoExtensions() ) ) ;
		
		// Check each video file for the presence of .srt, .sup, and .wav files.
		for( File videoFile : videoFiles )
		{
			final String srtFileName = videoFile.getName().replace( ".mkv", ".en.srt" ) ;
			final File srtFile = new File( videoFile.getParentFile(), srtFileName ) ;
			if( srtFile.exists() )
			{
				// Has .srt file -- skip
				continue ;
			}

			final String supFileName = videoFile.getName().replace( ".mkv", ".sup" ) ;
			final File supFile = new File( videoFile.getParentFile(), supFileName ) ;
			if( supFile.exists() )
			{
				// Has .sup, so the file has already been extracted but not yet OCR'd.
				addToDatabase_OCR( videoFile ) ;
				continue ;
			}

			final String wavFileName = videoFile.getName().replace( ".mkv", ".wav" ) ;
			final File wavFile = new File( videoFile.getParentFile(), wavFileName ) ;
			if( wavFile.exists() )
			{
				// Has .wav file, so it has been extracted but needs transcription.
				addToDatabase_Transcribe( videoFile ) ;
				continue ;
			}
			// PC: no .srt, .sup, or .wav: Need to extract
			addToDatabase_Extract( videoFile ) ;
		} // for( videoFile )

		log.info( "Number of files to extract: " + extractSubtitleCollection.countDocuments() ) ;
		log.info( "Number of files to OCR: " + createSRTWithOCRCollection.countDocuments() ) ;
		log.info( "Number of files to transcribe: " + createSRTWithTranscribeCollection.countDocuments() ) ;

		log.info( "Shutdown." ) ;
	}

	/**
	 * Evaluate a file to see if already has its subtitles extracted. Specifically, return true if any of the following
	 *  files are present for the given inputFile:
	 *  - fileName.en.srt
	 *  - fileName.en.sup
	 *  - fileName.wav
	 * Return false otherwise.
	 * @param log
	 * @param inputFile
	 * @return
	 */
	public static boolean subtitlesAlreadyExtracted( Logger log, final File inputFile )
	{
		assert( log != null ) ;
		assert( inputFile != null ) ;
		//		assert( inputFile.getAbsolutePath().endsWith( ".mkv" ) ) ;

		final String srtFileName = inputFile.getName().replace( ".mkv", ".en.srt" ) ;
		final File srtFile = new File( inputFile.getParentFile(), srtFileName ) ;
		if( srtFile.exists() )
		{
			return true ;
		}

		final String supFileName = inputFile.getName().replace( ".mkv", ".en.sup" ) ;
		final File supFile = new File( inputFile.getParentFile(), supFileName ) ;
		if( supFile.exists() )
		{
			return true ;
		}

		final String wavFileName = inputFile.getName().replace( ".mkv", ".wav" ) ;
		final File wavFile = new File( inputFile.getParentFile(), wavFileName ) ;
		if( wavFile.exists() )
		{
			return true ;
		}

		return false ;		
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

	public void addToDatabase_Transcribe( final File fileToTranscribe )
	{
		assert( fileToTranscribe != null ) ;

		createSRTWithTranscribeCollection.insertOne( new JobRecord_FileNameWithPath( fileToTranscribe ) ) ;
	}

	public void addToDatabase_Transcribe( final List< File > filesToOCR )
	{
		assert( filesToOCR != null ) ;

		// Build a JobRecord for each and then add all at once.
		List< JobRecord_FileNameWithPath > jobRecords = new ArrayList< JobRecord_FileNameWithPath >() ;
		filesToOCR.forEach( file -> jobRecords.add( new JobRecord_FileNameWithPath( file ) ) ) ;

		if( !jobRecords.isEmpty() )
		{
			createSRTWithTranscribeCollection.insertMany( jobRecords ) ;
		}
	}
}
