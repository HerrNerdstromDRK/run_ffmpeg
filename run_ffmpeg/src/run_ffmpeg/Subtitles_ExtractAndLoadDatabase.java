package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.MongoCollection;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;
import run_ffmpeg.ffmpeg.FFmpeg_Stream;

public class Subtitles_ExtractAndLoadDatabase
{
	/// Setup the logging subsystem
	protected Logger log = null ;
	protected Common common = null ;
	
	protected transient MoviesAndShowsMongoDB masMDB = null ;
	protected transient MongoCollection< FFmpeg_ProbeResult > createSRTWithAICollection = null ;
	protected transient MongoCollection< JobRecord_OCRFile > createSRTWithOCRCollection = null ;
	
	/// All FFmpeg_ProbeResults currently known, sorted by the absolute path.
	protected Map< String, FFmpeg_ProbeResult > allProbeInfoInstances = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_subtitles_loaddatabase.txt" ;

	/// If the file by the given name is present, stop this processing at the
	/// next iteration of the main loop.
	private static final String stopFileName = "C:\\Temp\\stop_subtitles_loaddatabase.txt" ;
	
	static final String codecTypeSubTitleNameString = "subtitle" ;
	static final String codecNameSubTitlePGSString = "hdmv_pgs_subtitle" ; // pgs image format; subtitle edit does a decent job of OCR
	static final String codecNameSubtitleHDMVString = "hdmv_text_subtitle" ; // outputs as .sup and can process like pgs
	static final String codecNameSubTitleSRTString = "subrip" ; // srt files
	static final String codecNameSubTitleDVDSubString = "dvd_subtitle" ; // vob sub image format; need to use other means (AI?); subtitle edit does a poor job of OCR
	static final String codecNameSubtitleMovText = "mov_text" ; // also shows as Timed Text in mp4 files.

	/// Identify the allowable languages for subtitles.
	static final String[] allowableSubTitleLanguages =
		{
				"eng",
				"en"
		} ;

	/// The subtitle codec names to be extracted.
	/// This will mostly be used when selecting which streams to extract as separate files.
	static final String[] extractableSubTitleCodecNames =
		{
				codecNameSubTitlePGSString,
				codecNameSubTitleSRTString,
				codecNameSubtitleMovText, // Roku can show this natively -- no need to extract.
				codecNameSubtitleHDMVString
		} ;
	
	public Subtitles_ExtractAndLoadDatabase()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		masMDB = new MoviesAndShowsMongoDB( log ) ;
		createSRTWithAICollection = masMDB.getAction_CreateSRTsWithAICollection() ;
		log.info( "AI database has " + createSRTWithAICollection.countDocuments() + " object(s) currently loaded." ) ;

		createSRTWithOCRCollection = masMDB.getAction_CreateSRTsWithOCRCollection() ;
		log.info( "OCR database has " + createSRTWithOCRCollection.countDocuments() + " object(s) currently loaded." ) ;

		allProbeInfoInstances = new HashMap< String, FFmpeg_ProbeResult >() ;
		
		initObject() ;
	}

	private void initObject()
	{
		log.info( "Loading all FFmpeg_ProbeResults..." ) ;
		final List< FFmpeg_ProbeResult > allProbeInfoInstancesList = masMDB.getAllProbeInfoInstances() ;
		log.info( "Loaded " + allProbeInfoInstancesList.size() + " instance(s)." ) ;
		
		log.info( "Sorting..." ) ;
		for( FFmpeg_ProbeResult theProbeResult : allProbeInfoInstancesList )
		{
			allProbeInfoInstances.put( theProbeResult.getFileNameWithPath(), theProbeResult ) ;
		}
		log.info( "Done." ) ;
	}
	
	public static void main( final String[] args )
	{
		(new Subtitles_ExtractAndLoadDatabase()).execute() ;
	}
	
	public void execute()
	{
		// Build the list of folders to extract and OCR
		List< String > foldersToExtractAndConvert = new ArrayList< String >() ;
//		foldersToExtractAndConvert.add( Common.getAllMediaFolders() ) ;
//		foldersToExtractAndConvert.add( Common.getPathToTVShows() ) ;
//		foldersToExtractAndConvert.add( Common.getPathToMovies() ) ;
		foldersToExtractAndConvert.add( Common.getPathToToOCR() ) ;
//		foldersToExtractAndConvert.add( "\\\\skywalker\\\\Media\\To_OCR\\Arrested Development (2003) {imdb-0367279} {tvdb-72173}\\Season 02" ) ;
		
		log.info( "Extracting subtitles in " + foldersToExtractAndConvert.toString() ) ;

		// Locate and add any .sup (or other) files to the database.
		final String[] imageFormatExtensions = { "sup" } ;
		final List< File > supInputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, imageFormatExtensions ) ;
		addToDatabase_OCR( supInputFiles ) ;
		log.info( "Added " + supInputFiles.size() + " .sup file(s) to database" ) ;
		
		final List< File > inputFiles = common.getFilesInDirectoryByExtension( foldersToExtractAndConvert, Common.getVideoExtensions() ) ;
		log.info( "Found " + inputFiles.size() + " file(s) from which to extract subtitles" ) ;
		
		for( File inputFile : inputFiles )
		{
			if( common.shouldStopExecution( stopFileName ) )
			{
				break ;
			}
			extractAndAddToDatabase( inputFile ) ;
		}
		log.info( "Number of srt files to generate via whisperX: " + createSRTWithAICollection.countDocuments() ) ;
		log.info( "Number of srt files to generate via OCR: " + createSRTWithOCRCollection.countDocuments() ) ;
		log.info( "Shutdown." ) ;
	}
	
	/**
	 * Extract subtitles from this file and then add any OCR or transcribe work to the database to be handled by distributed workers.
	 * @param inputFile
	 */
	public void extractAndAddToDatabase( final File inputFile )
	{
		assert( inputFile != null ) ;
		
		if( hasMatchingSRTFiles( inputFile ) )
		{
			return ;
		}
		
		if( findAndAddSUPFilesToDatabase( inputFile ) )
		{
			// Found matching sup file(s).
			// SUP added to OCR collection for processing; nothing further to accomplish here.
			return ;
		}
		
		final FFmpeg_ProbeResult inputProbeResult = getProbeResult( inputFile ) ;
		if( null == inputProbeResult )
		{
			log.warning( "Failed to probe file: " + inputFile.getAbsolutePath() ) ;
			return ;
		}
		
		extractSubtitles( inputProbeResult ) ;
	}
	
	public void addToDatabase_Transcribe( final FFmpeg_ProbeResult theProbeResult )
	{
		assert( theProbeResult != null ) ;
		
		createSRTWithAICollection.insertOne( theProbeResult ) ;
	}
	
	public void addToDatabase_OCR( final File fileToOCR )
	{
		assert( fileToOCR != null ) ;
		
		createSRTWithOCRCollection.insertOne( new JobRecord_OCRFile( fileToOCR ) ) ;
	}
	
	public void addToDatabase_OCR( final List< File > filesToOCR )
	{
		assert( filesToOCR != null ) ;
		
		for( File inputFile : filesToOCR )
		{
			addToDatabase_OCR( inputFile ) ;
		}
	}
	
	/**
	 * Return an FFmpeg_ProbeResult for this file. If not found in the database, it will be probed but not stored in the database.
	 * @param inputFile
	 * @return FFmpeg_ProbeResult for the given inputFile or null if ffprobe fails.
	 */
	public FFmpeg_ProbeResult getProbeResult( final File inputFile )
	{
		assert( inputFile != null ) ;
		
		FFmpeg_ProbeResult theProbeResult = allProbeInfoInstances.get( inputFile.getAbsolutePath() ) ;
		if( null == theProbeResult )
		{
			theProbeResult = common.ffprobeFile( inputFile, log ) ;
		}
		return theProbeResult ;
	}
	
	public boolean hasMatchingSRTFiles( final File inputFile )
	{
		assert( inputFile != null ) ;
		
		// First, check for the presence of .srt file(s).
		final File parentDir = inputFile.getParentFile() ;
		final String baseNameQuoted = Pattern.quote( FilenameUtils.getBaseName( inputFile.getName() ) ) ;
		final String srtPatternString = baseNameQuoted + "\\.en(?:\\.(\\d)+)?\\.srt" ;
		final Pattern srtPattern = Pattern.compile( srtPatternString ) ;
		final List< File > srtFiles = common.getFilesInDirectoryByRegex( parentDir, srtPattern ) ;
		
		if( !srtFiles.isEmpty() )
		{
			log.fine( "Found matching srt file(s) for file " + inputFile.getAbsolutePath() + "; skipping." ) ;
			return true ;
		}
		
		return false ;
	}
	
	public boolean findAndAddSUPFilesToDatabase( final File inputFile )
	{
		final File parentDir = inputFile.getParentFile() ;
		final String baseNameQuoted = Pattern.quote( FilenameUtils.getBaseName( inputFile.getName() ) ) ;
		final String supPatternString = baseNameQuoted + "\\.en\\..*\\.sup" ;
		final Pattern supPattern = Pattern.compile( supPatternString ) ;
		final List< File > supFiles = common.getFilesInDirectoryByRegex( parentDir, supPattern ) ;
		
		if( supFiles.isEmpty() )
		{
			// No SUP files to add
			return false ;
		}
		return true  ;
	}
	
	/**
	 * This is a subordinate method to help build the overall extract ffmpeg command.
	 * Build the command *option* string to extract subtitles from the given probeResult file.
	 * If no subtiles can be extracted (typically no pgs or srt streams), then return an empty structure.
	 * @param probeResult
	 * @param theFile
	 * @param supFiles
	 * @return
	 */
	public ImmutableList.Builder< String > buildFFmpegSubTitleExtractionOptionsString( FFmpeg_ProbeResult probeResult, final List< File > supFiles )
	{
		assert( probeResult != null ) ;
		assert( supFiles != null ) ;
		
		final File inputFile = new File( probeResult.getFileNameWithPath() ) ;
		
		// The ffmpegOptionsCommandString will contain only the options to extract subtitles
		// from the given FFmpegProbeResult
		// All of the actual ffmpeg command build ("ffmpeg -i ...") happens elsewhere
		ImmutableList.Builder< String > ffmpegOptionsCommandString = new ImmutableList.Builder< String >() ;

		// includedSubTitleStreams will include only allowable subtitle streams to extract
		final List< FFmpeg_Stream > extractableSubTitleStreams = findExtractableSubTitleStreams( probeResult ) ;

		// If the file has multiple subtitle streams that can be extracted, then ensure we name the
		// first such stream without a stream index #
		boolean processedFirstStream = false ;

		for( FFmpeg_Stream stStream : extractableSubTitleStreams )
		{
			// TEST: Looking for a way to determine if the file has a valid subtitle stream
			// Trying by checking if this subtitle stream has a tag named "NUMBER_OF_BYTES-eng" and its value is at least 250
			// -- lowest I've seen for a valid subtitle stream is 30025
			Map< String, String > tags = stStream.tags ;
			if( !tags.isEmpty() )
			{
				final String numberOfBytesEngString = tags.get( "NUMBER_OF_BYTES-eng" ) ;
				if( numberOfBytesEngString != null )
				{
//					log.info( "numberOfBytesEngString: " + numberOfBytesEngString ) ;
					Integer numberOfBytesEngInteger = Integer.valueOf( numberOfBytesEngString ) ;
					if( numberOfBytesEngInteger.intValue() < 250 )
					{
						// Invalid stream?
						log.info( "Skipping subtitle stream because its number of bytes is too small: " + numberOfBytesEngString + " < 250" ) ;
						continue ;
					}
				}
			}

			//			if( stStream.duration < 1.0 )
			//			{
			//				// No valid subtitle stream
			//				log.info( "Skipping subtitle stream due to short duration: " + stStream.duration ) ;
			//				continue ;
			//			}
			final int streamIndex = stStream.index ;

			// So far, I know of two subtitle types I can work with: pgs and srt
			// -map 0:$streamNumber -c:s copy "$supFileName"

			// Found PGS or SRT stream
			ffmpegOptionsCommandString.add( "-map", "0:" + streamIndex ) ;

			// Create the output filename
			// First, replace the .mkv with empty string: Movie (2000).mkv -> Movie (2009)
			//				String outputFileName = theFile.getMKVFileNameWithPath().replace( ".mkv", "" ) ;
			final String outputPath = FilenameUtils.getFullPath( inputFile.getAbsolutePath() ) ;
			String outputFileNameWithPath = common.addPathSeparatorIfNecessary( outputPath )
					+ FilenameUtils.getBaseName( inputFile.getAbsolutePath() ) ;

			// Movie (2009) -> Movie (2009).en.1.sup or Movie (2009).en.1.srt
			outputFileNameWithPath += ".en" ;
			if( extractableSubTitleStreams.size() > 1 )
			{
				// More than one stream -- include a stream id to the second and beyond streams
				if( !processedFirstStream )
				{
					// This is the first stream -- do NOT add a stream #
					processedFirstStream = true ;
				}
				else
				{
					// This is not the first subtitle stream in a file with multiple subtitle streams.
					// Add a stream index # to the filename
					outputFileNameWithPath += "." + streamIndex ;
				}
			}

			if( stStream.codec_name.equals( codecNameSubTitlePGSString ) || stStream.codec_name.equals( codecNameSubtitleHDMVString ))
			{
				outputFileNameWithPath += ".sup" ;
				supFiles.add( new File( outputFileNameWithPath ) ) ;
			}
			else if( stStream.codec_name.equals( codecNameSubTitleSRTString ) || stStream.codec_name.equals( codecNameSubtitleMovText ) )
			{
				outputFileNameWithPath += ".srt" ;
			}
			ffmpegOptionsCommandString.add( "-c:s", "copy", outputFileNameWithPath ) ;

		}
		log.fine( "ffmpegOptionsCommandString: "
				+ common.toStringForCommandExecution( ffmpegOptionsCommandString.build() ) ) ;
		return ffmpegOptionsCommandString ;	
	}

	/**
	 * Extract the subtitle streams from the given file with associated probeResult.
	 * This method will build and execute the ffmpeg command.
	 * @param fileToSubTitleExtract
	 * @param probeResult
	 */
	public void extractSubtitles( FFmpeg_ProbeResult probeResult )
	{
		assert( probeResult != null ) ;
		
		final File inputFile = new File( probeResult.getFileNameWithPath() ) ;
		
		// Build a set of options for an ffmpeg command based on the JSON input
		// If no suitable subtitles are found, the options string will be empty
		// supFileNames will hold the names of the sup files ffmpeg will generate
		List< File > supFiles = new ArrayList< File >() ;
		ImmutableList.Builder< String > subTitleExtractionOptionsString =
				buildFFmpegSubTitleExtractionOptionsString( probeResult, supFiles ) ;

		// If subTitleExtractionOptionsString is empty, then no usable subtitle streams were found
		if( subTitleExtractionOptionsString.build().isEmpty() )
		{
			//  Need to address this here.
			// No usable streams found
			// Extract the first audio stream as a wave file and conduct a whisperX transcription of it.
			log.info( " Extracting audio for file " + probeResult.getFileNameWithPath() ) ;
			
			final String wavFileNameWithPath = Common.replaceExtension( inputFile.getAbsolutePath(), "wav" ) ;
			final File wavFile = new File( wavFileNameWithPath ) ;

			// Only make the wav file if it is absent.
			// Pass -1 for the duration hours/mins/secs to extract the entire audio stream.
			if( !wavFile.exists() && !common.extractAudioFromAVFile( inputFile, wavFile, 0, 0, 0, -1, -1, -1 ) )
			{
				log.warning( " Failed to make wav file: " + wavFile.getAbsolutePath() ) ;
				return ;
			}

			// wav file exists, either through extracting here or finding in the directory
			// Either way, add it to the return list.
			addToDatabase_Transcribe( probeResult ) ;
//			theController.addFilesToTranscriptionPipeline( avFile ) ;
			return ;
		}

		// Build the ffmpeg command
		ImmutableList.Builder< String > ffmpegSubTitleExtractCommand = new ImmutableList.Builder<String>() ;
		ffmpegSubTitleExtractCommand.add( common.getPathToFFmpeg() ) ;
		ffmpegSubTitleExtractCommand.add( "-y" ) ;
		ffmpegSubTitleExtractCommand.add( "-analyzeduration", Common.getAnalyzeDurationString() ) ;
		ffmpegSubTitleExtractCommand.add( "-probesize", Common.getProbeSizeString() ) ;
		ffmpegSubTitleExtractCommand.add( "-i", inputFile.getAbsolutePath() ) ;
		ffmpegSubTitleExtractCommand.addAll( subTitleExtractionOptionsString.build() ) ;

		boolean executeSuccess = common.executeCommand( ffmpegSubTitleExtractCommand ) ;
		if( !executeSuccess )
		{
			log.warning( "Failed to extract PGS: " + inputFile.getAbsolutePath() ) ;
		}
		else
		{
			// Successful extract.
			// Prune the current folder for small sup files.
			PruneSmallSUPFiles pssf = new PruneSmallSUPFiles() ;
			final File regularFile = new File( inputFile.getAbsolutePath() ) ;
			pssf.pruneFolder( regularFile.getParent() ) ;

			// Now pass any remaining sup files to the pipeline for OCR and beyond.
			List< File > remainingSupFiles = new ArrayList< File >() ;
			for( File checkFile : supFiles )
			{
				if( checkFile.exists() )
				{
					remainingSupFiles.add( checkFile ) ;
				}
				else
				{
					// .sup file was purged indicating it was too small
					probeResult.setSmallSubtitleStreams( true ) ;
				}
			}

			// If a subtitle stream was found that is too small to keep then record it in the database.
//			if( probeResult.getSmallSubtitleStreams() )
//			{
//				// Found one more small subtitle files
//				log.fine( "Found small subtitle stream for file " + fileToSubTitleExtract.getInputFileNameWithPath() ) ;
//
//				MongoCollection< FFmpeg_ProbeResult > probeInfoCollection = theController.getProbeInfoCollection() ;
//				DeleteResult deleteResult = probeInfoCollection.deleteOne( Filters.eq( "_id", probeResult._id ) ) ;
//				if( deleteResult.getDeletedCount() > 0 )
//				{
//					// FFmpegProbeResult was in the database; ok to insert the replacement
//					// Note that the probe result could be absent from the database if we are extracting subtitles
//					//  from a folder that is not kept in the database.
//					probeInfoCollection.insertOne( probeResult ) ;
//				}
//			}

			// The addFilesToPipeline() method will handle a null pipeline.
			addToDatabase_OCR( remainingSupFiles ) ;
		}
	}

	/**
	 * Walk through the list of streams, find and return the subtitle streams that can be extracted.
	 * These are generally the PGS and SRT streams, and in English only.
	 * Will always return a non-null list, although it may be empty.
	 * @param probeResult
	 * @return
	 */
	public List< FFmpeg_Stream > findExtractableSubTitleStreams( FFmpeg_ProbeResult probeResult )
	{
		List< FFmpeg_Stream > extractableSubTitleStreams = new ArrayList< FFmpeg_Stream >() ;

		for( FFmpeg_Stream theStream : probeResult.streams )
		{
			log.fine( "Checking stream: " + theStream ) ;
			if( !theStream.codec_type.equals( codecTypeSubTitleNameString ) )
			{
				// Not a subtitle stream
				log.fine( "Ignoring stream: " + theStream ) ;
				continue ;
			}
			// Post condition: theStream is a subtitle stream

			// Check for a language tag
			if( theStream.tags.containsKey( "language" ) )
			{
				boolean isAllowableLanguage = isAllowableSubTitleLanguage( theStream.tags.get( "language" ) ) ;
				if( !isAllowableLanguage )
				{
					// Has a language tag, but it is not allowable
					log.fine( "Found subtitle with language tag but NOT allowable: "
							+ theStream.tags.get( "language" ) ) ;
					continue ;
				}
			}
			// Post condition: this is a subtitle stream with an allowable language

			if( isExtractableSubTitleCodecName( theStream.codec_name ) )
			{
				// Found allowable subtitle type
				log.fine( "Found allowable subtitle stream: " + theStream ) ;
				extractableSubTitleStreams.add( theStream ) ;
			}
		} // for( stream )
		return extractableSubTitleStreams ;
	}
	
	public static boolean isAllowableSubTitleLanguage( final String audioLanguage )
	{
		for( String allowableLanguage : allowableSubTitleLanguages )
		{
			if( allowableLanguage.equalsIgnoreCase( audioLanguage ) )
			{
				// Found an allowable language
				return true ;
			}
		}
		// No allowable language found
		return false ;
	}

	public static boolean isExtractableSubTitleCodecName( final String stCodeName )
	{
		for( String allowableCodecName : extractableSubTitleCodecNames )
		{
			if( allowableCodecName.equalsIgnoreCase( stCodeName ) )
			{
				// Found an allowable code name
				return true ;
			}
		}
		// No allowable code name found
		return false ;
	}
}
