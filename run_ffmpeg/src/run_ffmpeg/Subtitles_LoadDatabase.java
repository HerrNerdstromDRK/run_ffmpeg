package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

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
		foldersToExtractAndConvert.add( Common.getPathToOCR() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToStaging() ) ;
		//		foldersToExtractAndConvert.add( "C:\\Temp" ) ;
		//		foldersToExtractAndConvert.add( "\\\\skywalker\\Media\\TV_Shows\\A Pup Named Scooby-Doo (1988) {tvdb-73546}" ) ;

		log.info( "Loading files from which to extract subtitles in " + foldersToExtractAndConvert.toString() ) ;

		// Find and load video files (mkv/mp4/etc.) into the database to extract.
		log.info( "Searching for video files to extract..." ) ;

		final List< File > videoFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, Common.getVideoExtensions() ) ;
		log.info( "Found " + videoFiles.size() + " video file(s) with extension " + Arrays.toString( Common.getVideoExtensions() ) ) ;
		
		// Check each video file for the presence of .srt, .sup, and .wav files.
		for( File videoFile : videoFiles )
		{
			// Check for .sup/.wav files individually -- it's possible that a previous workflow has ocr'd/transcribed only
			// one or several, but not all, of the .sup/.wav files for any given videoFile.
			// Check for matching sup files to OCR.
			final List< File > matchingSupFiles = getMatchingFiles( videoFile, "sup", true ) ;
			if( !matchingSupFiles.isEmpty() )
			{
				addToDatabase_OCR( matchingSupFiles ) ;
			}
			
			final List< File > matchingWavFiles = getMatchingFiles( videoFile, "wav", false ) ;
			if( !matchingWavFiles.isEmpty() )
			{
				addToDatabase_Transcribe( matchingWavFiles ) ;
			}

			// If this video file has any matching .sup, .wav, or .srt files, then it has already been extracted.
			if( !matchingSupFiles.isEmpty() || !matchingWavFiles.isEmpty() )
			{
				// Has a matching .sup or .wav file, no need to extract.
				continue ;
			}
			
			// Check for matching .srt files
			final List< File > matchingSRTFiles = getMatchingFiles( videoFile, "srt", true ) ;
			if( !matchingSRTFiles.isEmpty() )
			{
				// No need to extract.
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
	 * Return a list of files in the videoFile's current directory with videoFile's name (all but extension)
	 *  with the given extensionToMatch replaced.
	 * For example, if videoFile is "Maleficent Mistress Of Evil (2019) {tmdb-420809}.mkv", and extensionToMatch is "sup",
	 *  return all files that match "Maleficent Mistress Of Evil (2019) {tmdb-420809}.*sup" (regex).
	 * @param videoFile
	 * @param extensionToMatch
	 * @return
	 */
	public List< File > getMatchingFiles( final File videoFile, final String extensionToMatch, final boolean searchForTwoPeriods )
	{
		final String videoFileExtension = FilenameUtils.getExtension( videoFile.getAbsolutePath() ) ;
		final String videoFileNameWithoutExtensionOrDot = videoFile.getName().replace( "." + videoFileExtension, "" ) ;
		final String quotedVideoFileNameWithoutExtensionOrDot = Pattern.quote( videoFileNameWithoutExtensionOrDot ) ;
		
		// Two patterns exist here: File name-other.en.srt and File name-other.wav
		// In the first instance, using two periods (between name and en, and between en and srt) is appropriate
		// In the second case, only one period is appropriate.
		// Trying to check for two periods in the second instance will simply not match files that exist, whereas
		// not searching for two periods in the first instance will create false matches.
		String matchPatternString = quotedVideoFileNameWithoutExtensionOrDot + "\\." ;
		if( searchForTwoPeriods ) matchPatternString += ".*\\." + extensionToMatch ;
		final Pattern matchPattern = Pattern.compile( matchPatternString ) ;
		
		List< File > matchingFiles = common.getFilesInDirectoryByRegex( videoFile.getParentFile(), matchPattern ) ;
		return matchingFiles ;
	}
	
	/**
	 * Same as the other form for this method except default behavior is to only search for a single period.
	 * @param videoFile
	 * @param extensionToMatch
	 * @return
	 */
	public List< File > getMatchingFiles( final File videoFile, final String extensionToMatch )
	{
		return getMatchingFiles( videoFile, extensionToMatch, false ) ;
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
