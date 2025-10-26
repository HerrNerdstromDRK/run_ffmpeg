package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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
		//		foldersToExtractAndConvert.add( Common.getAllMediaFolders() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToTVShows() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToMovies() ) ;
		foldersToExtractAndConvert.add( Common.getPathToOCR() ) ;
		//		foldersToExtractAndConvert.add( Common.getPathToStaging() ) ;
		//		foldersToExtractAndConvert.add( "\\\\skywalker\\Media\\TV_Shows\\Silo (2023) {imdb-14688458} {tvdb-403245}" ) ;
		//		foldersToExtractAndConvert.add( "\\\\skywalker\\Media\\TV_Shows\\A Pup Named Scooby-Doo (1988) {tvdb-73546}" ) ;

		log.info( "Extracting subtitles in " + foldersToExtractAndConvert.toString() ) ;

		// Locate and add any .sup files to the database for OCR.
		//		{
		//			log.info( "Searching for .sup files..." ) ;
		//			final String[] imageFormatExtensions = { "sup" } ;
		//			final List< File > supInputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, imageFormatExtensions ) ;
		//
		//			addToDatabase_OCR( supInputFiles ) ;
		//
		//			log.info( "Added " + supInputFiles.size() + " .sup file(s) to database" ) ;
		//		}

		// Locate and add any .wav files to the database for AI transcription.
		//		{
		//			log.info( "Searching for .wav files..." ) ;
		//			final String[] audioFormatExtensions = { "wav" } ;
		//			final List< File > wavInputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, audioFormatExtensions ) ;
		//			
		//			
		//			log.info( "Added " + wavInputFiles.size() + " .wav file(s) to database" ) ;
		//		}

		// Find and load video files (mkv/mp4/etc.) into the database to extract.
		{
			log.info( "Searching for video files to extract..." ) ;
			final List< File > videoFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, Common.getVideoExtensions() ) ;
			log.info( "Found " + videoFiles.size() + " video file(s)." ) ;

			// Prune videoFiles for any video files that already have at least one associated srt, wav, or sup file.
			// The presence of any of these files indicates the file either has an srt file or is currently undergoing an srt build.
			// First, get all of the srt/wav/sup files in these directories
			//			log.info( "Finding srt files..." ) ;
			final List< File > srtFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, "srt" ) ;
			log.info( "Found " + srtFiles.size() + " srt file(s)." ) ;

			//			log.info( "Finding sup files..." ) ;
			final List< File > supFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, "sup" ) ;
			addToDatabase_OCR( supFiles ) ;
			log.info( "Found " + supFiles.size() + " sup file(s)." ) ;

			//			log.info( "Finding wav files..." ) ;
			final List< File > wavFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, "wav" ) ;
			addToDatabase_Transcribe( wavFiles ) ;
			log.info( "Found " + wavFiles.size() + " wav file(s)." ) ;

			// Add the srt files into a Map that is sorted by the srtFile's parent directory
			Multimap< File, File > srtFilesByDirectory = ArrayListMultimap.create() ;
			srtFiles.forEach( file -> srtFilesByDirectory.put( file.getParentFile(),  file ) ) ;
			//			log.info( "srtFilesByDirectory.size: " + srtFilesByDirectory.size() ) ;

			// Do the same for .sup and .wav files
			Multimap< File, File > supFilesByDirectory = ArrayListMultimap.create() ;
			supFiles.forEach( file -> supFilesByDirectory.put( file.getParentFile(),  file ) ) ;
			//			log.info( "supFilesByDirectory.size: " + supFilesByDirectory.size() ) ;

			Multimap< File, File > wavFilesByDirectory = ArrayListMultimap.create() ;
			wavFiles.forEach( file -> wavFilesByDirectory.put( file.getParentFile(),  file ) ) ;
			//			log.info( "wavFilesByDirectory.size: " + wavFilesByDirectory.size() ) ;

			// Remove from the videoFiles structure any files that have an associated srt/wav/sup file
			List< File > videoFilesPruned = pruneVideoFilesForExtraction( videoFiles, srtFilesByDirectory, supFilesByDirectory, wavFilesByDirectory ) ; 

			// This will add the files to the database regardless if .srt files exist for them.
			// This is on purpose to simplify code here -- the file check code must live somewhere, so I choose to keep this
			//  code cleaner/smaller...for no particular reason.
			addToDatabase_Extract( videoFilesPruned ) ;

			log.info( "Number of files to extract: " + extractSubtitleCollection.countDocuments() ) ;
			log.info( "Number of files to OCR: " + createSRTWithOCRCollection.countDocuments() ) ;
			log.info( "Number of files to transcribe: " + createSRTWithTranscribeCollection.countDocuments() ) ;
		}

		log.info( "Shutdown." ) ;
	}

	/**
	 * Return the files listed in filesInMatchingDirectory that match the given Pattern.
	 */
	public List< File > findMatchingFilesRegex( final Pattern thePattern, final Collection< File > filesInMatchingDirectory )
	{
		assert( thePattern != null ) ;
		assert( filesInMatchingDirectory!= null ) ;

		List< File > matchingFiles = new ArrayList< File >() ;

		for( File fileInSameDirectory : filesInMatchingDirectory )
		{
			final Matcher theMatcher = thePattern.matcher( fileInSameDirectory.getName() ) ;
			if( theMatcher.find() )
			{
				// Found a match
				matchingFiles.add( fileInSameDirectory ) ;
			}
		}
		return matchingFiles ;
	}

	public List< File > findVideoFilesWithoutMatchingSRTFile( final List< File > videoFiles, Multimap< File, File > srtFilesByDirectory )
	{
		assert( videoFiles != null ) ;
		assert( srtFilesByDirectory != null ) ;

		List< File > srtFilesPruned = new ArrayList< File >() ;

		for( File videoFile : videoFiles )
		{
			// Search for srt files in the video file's directory that match the video file name pattern.
			// File pattern will be for the file name only, excluding the path.
			final String baseNameQuoted = Pattern.quote( FilenameUtils.getBaseName( videoFile.getName() ) ) ;
			final String srtPatternString = baseNameQuoted + "\\.en(?:\\.(\\d)+)?\\.srt" ;
			final Pattern srtPattern = Pattern.compile( srtPatternString ) ;

			// Get the srt files in the same directory as the video file.
			final Collection< File > srtFilesInVideoDirectory = srtFilesByDirectory.get( videoFile.getParentFile() ) ;

			// For each srt file in the video file directory, check for a matching srt file.
			boolean foundMatch = false ;
			for( File srtFileInVideoDirectory : srtFilesInVideoDirectory )
			{
				final Matcher srtMatcher = srtPattern.matcher( srtFileInVideoDirectory.getName() ) ;
				if( srtMatcher.find() )
				{
					foundMatch = true ;
					break ;
				}
			}
			// Done looking for matching srt files in the video's directory.
			if( !foundMatch )
			{
				// No matching srt file found, add it to the list.
				srtFilesPruned.add( videoFile ) ;
			}				
		}
		return srtFilesPruned ;
	}

	/**
	 * Look through the list of Files in videoFiles and the three *FilesByDirectory. For each srt/sup/wav file found, omit it from the prunedFiles
	 *  list. Return the list of files with no associated srt/sup/wav files.
	 * @param videoFiles
	 * @param srtFilesByDirectory
	 * @param supFilesByDirectory
	 * @param wavFilesByDirectory
	 * @return
	 */
	public List< File > pruneVideoFilesForExtraction( final List< File > videoFiles,
			final Multimap< File, File > srtFilesByDirectory,
			final Multimap< File, File > supFilesByDirectory,
			final Multimap< File, File > wavFilesByDirectory )
	{
		assert( videoFiles != null ) ;
		assert( srtFilesByDirectory != null ) ;
		assert( supFilesByDirectory != null ) ;
		assert( wavFilesByDirectory != null ) ;

		List< File > prunedFiles = new ArrayList< File >() ;

		for( File videoFile : videoFiles )
		{
			if( !subtitlesAlreadyExtracted( log, videoFile ) )
			{
				prunedFiles.add( videoFile ) ;
			}
			//			final String baseNameQuoted = Pattern.quote( FilenameUtils.getBaseName( videoFile.getName() ) ) ;
			//			final String srtPatternString = baseNameQuoted + "\\.en(?:\\.[\\d]+)?\\.srt" ;
			//			final Pattern srtPattern = Pattern.compile( srtPatternString ) ;
			//			
			//			final String wavPatternString = baseNameQuoted + "\\.wav" ;
			//			final Pattern wavPattern = Pattern.compile( wavPatternString ) ;
			//			
			//			final String supPatternString = baseNameQuoted + "\\.sup" ;
			//			final Pattern supPattern = Pattern.compile( supPatternString ) ;
			//	
			//			if( findMatchingFilesRegex( srtPattern, srtFilesByDirectory.get( videoFile.getParentFile() ) ).isEmpty()
			//					&& findMatchingFilesRegex( wavPattern, wavFilesByDirectory.get( videoFile.getParentFile() ) ).isEmpty()
			//					&& findMatchingFilesRegex( supPattern, supFilesByDirectory.get( videoFile.getParentFile() ) ).isEmpty() )
			//			{
			//				prunedFiles.add( videoFile ) ;
			//			}
		} // for( videoFile )
		return prunedFiles ;
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
