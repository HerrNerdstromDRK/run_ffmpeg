package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

public class TranscodeCommon
{
	private transient Logger log = null ;
	private transient Common common = null ;
	private String mkvInputDirectory = null ;
	private String mkvFinalDirectory = null ;
	private String mp4OutputDirectory = null ;
	private String mp4FinalDirectory = null ;
	
	/// Set whether or not to transcode video
	/// This is usually true, however the option here to disable video transcode is intended to be used
	/// for testing audio or subtitle functions -- the overall transcode will be much faster without
	/// transcoding video, allowing me to focus on the other areas.
	private boolean doTranscodeVideo = true ;
	
	/// Set to true to enable de-interlacing
	private boolean deInterlaceInput = false ;
	
	/// Extensions to transcode to mp4
	private final String[] transcodeExtensions = { ".mkv", ".MOV", ".mov", ".wmv" } ;
	
	/// The string that each forced subtitle SRT file name includes
	private final String forcedSubTitleFileNameContains = ".forced_subtitle." ;
	
	/// The array of language types to include under audioStreamsByName option above
	private final String[] audioStreamsByNameArray = { "eng", "en" } ;
	
	/// The transcode library to use for audio transcodes. libfdk is the highest quality, with aac as the second highest quality.
	private final String audioTranscodeLibrary = "aac" ;
	
	/// Set to true if we are to add a stereo stream of the primary audio language; false otherwise.
	private final boolean addAudioStereoStream = true ;
	
	/// Identify the audio stream transcoding options. Select one.
	public enum audioStreamTranscodeOptionsType
	{
		// Transcode all audio streams available in the input file
		audioStreamAll,
		
		// Transcode only the English (en/eng) audio streams
		audioStreamEnglishOnly,
		
		// Transcode the primary language audio streams, plus english. This is intended for foreign language movies
		// such as Chinese martial arts movies. In those cases, I tend to use the foreign language audio with
		// english subtitles, but also want the option of english audio.
		audioStreamPrimaryPlusEnglish,
		
		// Transcode by including all languages listed in an array of language names
		audioStreamsByName
	} ;
	private final audioStreamTranscodeOptionsType audioStreamTranscodeOptions = audioStreamTranscodeOptionsType.audioStreamsByName ;
	
	public TranscodeCommon( Logger log, Common common, final String mkvInputDirectory,
			final String mkvFinalDirectory,
			final String mp4OutputDirectory,
			final String mp4FinalDirectory  )
	{
		assert( mkvInputDirectory != null ) ;
		assert( !mkvInputDirectory.isBlank() ) ;
		
		this.log = log ;
		this.common = common ;
		
		// Some uses of the TranscodeFile do not use the last three directories.
		// However, they should still be valid.
		this.mkvInputDirectory = mkvInputDirectory ;
		this.mkvFinalDirectory = mkvFinalDirectory.isBlank() ? mkvInputDirectory : mkvFinalDirectory ;
		this.mp4OutputDirectory = mp4OutputDirectory.isBlank() ? mkvInputDirectory : mp4OutputDirectory ;
		this.mp4FinalDirectory = mp4FinalDirectory.isBlank() ? mkvInputDirectory : mp4FinalDirectory ;
	}
	
	/**
	 * Build the audio transcode options.
	 * The main goal here is to ensure a Stereo stream exists.
	 * @param inputFile
	 * @return
	 */
	public ImmutableList.Builder< String > buildAudioTranscodeOptions( TranscodeFile inputFile )
	{
		/*
		 * Example syntax:
		 *  ffmpeg -y -i "%%A" -map 0:v -c:v copy -map 0:a:0? -c:a:0 copy -map 0:a:0? -c:a:1 aac -b:a:1 192k -ac 2
		 *   -metadata:s:a:1 title="Eng 2.0 Stereo" -map 0:a:1? -c:a:2 copy -map 0:a:2? -c:a:3 copy -map 0:a:3?
		 *   -c:a:4 copy -map 0:a:4? -c:a:5 copy -map 0:a:5? -c:a:6 copy -map 0:a:6? -c:a:7 copy -map 0:s? -c:s copy
		 *   "ffmpegOut/%%~nA_Stereo.mkv"
		 */
		ImmutableList.Builder< String > audioTranscodeOptions = new ImmutableList.Builder<String>() ;
	
		// The method works in two phases:
		//  1) Build the list of audio streams to transcode based on the audioStreamTranscodeOptions
		//  2) Build the options to support the list of audio streams to be transcoded
		// NOTE: I have found that at least some of the movies/shows with stereo audio, at least on blu ray,
		//  have the director commentary in stereo, but not necessarily the primary sound.
		//  As such, I will generally ignore any existing stereo audio streams when deciding whether to add
		//  a stereo audio stream.
		ArrayList< FFmpegStream > audioStreamsToTranscode = new ArrayList< FFmpegStream >() ;
		
		if( audioStreamTranscodeOptionsType.audioStreamAll == audioStreamTranscodeOptions )
		{
			// Include all audio streams
			audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( "*" ) ) ;
		}
		else {
			if( audioStreamTranscodeOptionsType.audioStreamEnglishOnly == audioStreamTranscodeOptions )
			{
				audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( "eng" ) ) ;
				audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( "en" ) ) ;
			}
			else {
				if( audioStreamTranscodeOptionsType.audioStreamPrimaryPlusEnglish == audioStreamTranscodeOptions )
				{
					// Add the primary language first, then English
					// Often the primary language type will be English
					final String primaryAudioLanguage = inputFile.getPrimaryAudioLanguage() ;
					audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( primaryAudioLanguage ) ) ;
					if( !primaryAudioLanguage.equalsIgnoreCase( "eng" ) && !primaryAudioLanguage.equalsIgnoreCase( "en" ) )
					{
						// Primary language is other than english
						// Add english to the list (after the primary)
						audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( "eng" ) ) ;
						audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( "en" ) ) ;
					}
				}
				else {
					if( audioStreamTranscodeOptionsType.audioStreamsByName == audioStreamTranscodeOptions )
					{
						for( String languageName : audioStreamsByNameArray )
						{
							audioStreamsToTranscode.addAll( inputFile.getAudioStreamsByLanguage( languageName ) ) ;
						}
					}
					else
					{
						log.info( "Unknown audioStreamTranscode option: " + audioStreamTranscodeOptions ) ;
					}
					// Post condition: audioStreamsToTranscode now has the full list of audio streams to transcode into
					// the output file.
				}
			}
		}
		
		// Use a deliberate audio stream mapping regardless of our decision to add a stereo stream.
		// Typically, the first (#0) audio stream is the highest quality
		// Keep the first (#0) audio stream and convert it based on the prescribed library
		
		// Finally, copy the remaining streams as aac into the output, offset by one stream
		for( int inputStreamNumber = 0, outputStreamNumber = 0 ; inputStreamNumber < inputFile.getNumAudioStreams() ;
				++inputStreamNumber, ++outputStreamNumber )
		{
			audioTranscodeOptions.add( "-map", "0:a:" + inputStreamNumber ) ;
			audioTranscodeOptions.add( "-c:a:" + outputStreamNumber, audioTranscodeLibrary ) ;
			audioTranscodeOptions.add( "-metadata:s:a:" + outputStreamNumber,
					"title=\"" + inputFile.getAudioStreamAt( inputStreamNumber ).getTagByName( "title" ) + "\"" ) ;
			
			if( (0 == inputStreamNumber) && addAudioStereoStream )
			{
				// First audio stream (highest quality) and we need to add a stereo stream.
				// Copy the first (#0) audio stream into stream #1 as stereo (-ac 2)
				audioTranscodeOptions.add( "-map", "0:a:0" ) ;
				audioTranscodeOptions.add( "-c:a:1", audioTranscodeLibrary, "-ac:a:1", "2" ) ;
				audioTranscodeOptions.add( "-metadata:s:a:1", "title=\"Stereo\"" ) ;
				// Skip one output stream number
				++outputStreamNumber ;
			}
		}
		return audioTranscodeOptions ;
	}

	/**
	 * Build a list of ffmpeg mapping options to incorporate any subtitle (.srt) files into the transcode.
	 * Excludes importing the srt files themselves.
	 * @param theTranscodeFile
	 * @return
	 */
	public ImmutableList.Builder< String > buildSubTitleTranscodeOptions( TranscodeFile theTranscodeFile )
	{
		// "${srtInputFiles[@]}" -map 0:v -map 0:a -map -0:s "${srtMapping[@]}" -copyts
		ImmutableList.Builder< String > subTitleTranscodeOptions = new ImmutableList.Builder< String >() ;
	
		// Part of my transcode workflow process is to extract .srt files into separate files,
		// or to extract PGS streams into .sup files and OCR Them.
		// As such, never use default subtitle streams -- exclude them all explicitly.
		subTitleTranscodeOptions.add( "-map", "-0:s" ) ;
	
		// Now add the mapping for each SRT file
		// TODO/NOTE: This assumes the same iteration for this loop as with the input options.
		// In practice it doesn't matter since each of the SRT input files only has a single input stream.
		// inputFileMappingIndex is the zero-based index of the input file.
		// inputFileMappingIndex 0 is the source .mkv file
		// inputFileMappingIndex 1 here is the first SRT input file
		// inputFileMappingIndex is kept outside the loop here since that index only applies (I think) to files
		//  included via -i on the command line.
		// For forced subtitles, I will be including them implicitly, in which case (I think) they are not counted as
		//  an input file by index.
		// To avoid confusing the ffmpeg command line parser, first walk through all the srt files that
		//  are NOT forced subtitle, and finish those with a copy statement.
		//  The reason for this is that the -c:s mov_text needs to be attached to the non-forced subtitles, and not
		//  the forced subtitles.
		// Afterward, handle the forced subtitles.
		boolean foundNonForcedSubtTileStream = false ;
		int inputFileMappingIndex = 1 ;
		for( int srtFileIndex = 0 ; srtFileIndex < theTranscodeFile.numSRTInputFiles() ; ++srtFileIndex )
		{
			final File theSRTFile = theTranscodeFile.getSRTFile( srtFileIndex ) ;
			if( !theSRTFile.getName().contains( forcedSubTitleFileNameContains ) )
			{
				subTitleTranscodeOptions.add( "-map", "" + inputFileMappingIndex + ":s" ) ;
				subTitleTranscodeOptions.add( "-metadata:s:s:" + inputFileMappingIndex, "language=eng" ) ;
				subTitleTranscodeOptions.add( "-metadata:s:s:" + inputFileMappingIndex, "title=\"eng\"" ) ;
				++inputFileMappingIndex ;
				foundNonForcedSubtTileStream = true ;
			}
		}
		if( foundNonForcedSubtTileStream )
		{
			// Found at least one non-forced subtitle
			subTitleTranscodeOptions.add( "-c:s", "mov_text" ) ;
		}
		
		/*
		 * Note that forced subtitles are currently handled as a video option since they are burned into the
		 * video stream. No need to process them here.
		// Now walk through the srt files and process forced subtitle(s).
		for( int srtFileIndex = 0 ; srtFileIndex < theTranscodeFile.numSRTInputFiles() ; ++srtFileIndex )
		{
			final File theSRTFile = theTranscodeFile.getSRTFile( srtFileIndex ) ;
			String theSRTFileName = theSRTFile.getAbsolutePath() ;
			
			if( theSRTFileName.contains( forcedSubTitleFileNameContains ) )
			{
				// The subtitles option is a value of a filter graph argument, so
				//  we have to add an escape character before every special character (thanks stackoverflow!)
				// The correct use of the subtitles option is:
				// "subtitles='name of file with special characters replace with \specialcharacter'"
				// The special characters here include: :, \, [, ]
				// Note that ' is also a special character in ffmpeg filter option strings, but I don't allow it
				//  in any file name (along with & and a few others)
				theSRTFileName = theSRTFileName.replace( "\\", "\\\\" ) ;
				theSRTFileName = theSRTFileName.replace( ":", "\\:" ) ;
				theSRTFileName = theSRTFileName.replace( "[", "\\[" ) ;
				theSRTFileName = theSRTFileName.replace( "]", "\\]" ) ;
	
				log.info( "buildSubTitleTranscodeOptions> Found forced subtitle, heSRTFileName after replace(): " + theSRTFileName ) ;
				subTitleTranscodeOptions.add( "-vf", "\"subtitles=\'" + theSRTFileName + "\'\"" ) ;
			}
		} // for ( forced sub titles )
		*/
	
		return subTitleTranscodeOptions ;
	}

	/**
	 * Include the options for transcoding
	 * The standard is that the options strings include no leading or trailing spaces
	 *  -v yadif=1: de-interlace
	 *  -copyts: Copies the timestamps into the output file for use by subtitles
	 *  -vcodec libx264: Use H.264 codec for otuput
	 *  -crf 17: Set quality; anything better than 17 will not be noticeable
	 *  -map 0: Copy all streams, including subtitles
	 *  -movflags +faststart: Include indexing so it's easy to move anywhere in the file during playback
	 * @param inputFile
	 * @return
	 */
	public ImmutableList.Builder< String > buildVideoTranscodeOptions( TranscodeFile inputFile )
	{
		ImmutableList.Builder< String > videoTranscodeOptions = new ImmutableList.Builder<String>() ;
	
		// Forced subtitles are to be burned into the underlying video stream, so treat them here
		if( inputFile.hasForcedSubTitleFile() )
		{
			// Burn-in the forced subtitle track drawn from the stream in the input file.
			// Prefer this solution to using the .srt file since the .srt file may have OCR errors.
			// The normal approach here is to use [0:v][0:s[:stream#]], but since the subtitle files have the
			//  file-level stream index, we need to exclude the :s in the stream number prelude
			videoTranscodeOptions.add( "-filter_complex", "\"[0:v][0:" + inputFile.getForcedSubTitleStreamNumber() + "]overlay[v1]\"" ) ;
			videoTranscodeOptions.add( "-map", "\"[v1]\"" ) ;
			// Note that the presence of a forced subtitle for burn in means we lack the option of
			// not transcoding the video stream
		}
		else
		{
			// No forced subtitle stream
			// Set the basic transcode options
			videoTranscodeOptions.add( "-map", "0:v" ) ;
			if( !isDoTranscodeVideo() )
			{
				// Do NOT transcode video, just copy it
				// Probably working on audio or subtitle functions here and need to make the overall transcode
				// go as fast as possible. Just copying the video does that pretty well.
				videoTranscodeOptions.add( "-c:v", "copy" ) ;
			}
		} // if( hasForcedSubTitle )
		
		// Note that disabling transcode video options will fail when forced subtitles are present
		// Leave it to the user about the option space
		if( doTranscodeVideo )
		{
			if( isDeInterlaceInput() )	videoTranscodeOptions.add( "-vf", "yadif=1" ) ;
			videoTranscodeOptions.add( "-vcodec", "libx264" ) ;
			videoTranscodeOptions.add( "-crf", "17" ) ;
			videoTranscodeOptions.add( "-movflags", "+faststart" ) ;
			videoTranscodeOptions.add( "-metadata", "title=\"" + inputFile.getMetaDataTitle() + "\"" ) ;
		}
		else
		{
			if( inputFile.hasForcedSubTitleFile() )
			{
				log.info( "buildVideoTranscodeOptions> Video transcode is disabled while forced subtitles exist. This will"
						+ " probably generate an ffmpeg error" ) ;
			}
		}
		return videoTranscodeOptions ;
	}

	public String[] getTranscodeExtensions() {
		return transcodeExtensions;
	}

	public List< TranscodeFile > getTranscodeFilesInDirectory( final String inputDirectory, final String[] transcodeExtensions )
    {
    	return getTranscodeFilesInDirectory( new File( inputDirectory ), transcodeExtensions ) ;
    }

    public List< TranscodeFile > getTranscodeFilesInDirectory( final File inputDirectory, final String[] transcodeExtensions )
    {
    	List< TranscodeFile > transcodeFilesInDirectory = new ArrayList< >() ;
    	for( String extension : transcodeExtensions )
    	{
    		List< File > filesByExtension = common.getFilesInDirectoryByExtension( inputDirectory.getAbsolutePath(), extension ) ;
    		for( File theFile : filesByExtension )
    		{
    			TranscodeFile newTranscodeFile = new TranscodeFile( theFile,
    					mkvFinalDirectory,
    					mp4OutputDirectory,
    					mp4FinalDirectory,
    					log,
    					this ) ;
    			transcodeFilesInDirectory.add( newTranscodeFile ) ;
    		}
    	}
    	return transcodeFilesInDirectory ;
    }
    

	public List< TranscodeFile > surveyInputDirectoryAndBuildTranscodeFiles( final String inputDirectory,
			final String[] transcodeExtensions )
	{
		assert( inputDirectory != null ) ;
	
		List< TranscodeFile > filesToTranscode = new ArrayList< >() ;
	
		// inputDirectory could be:
		// - Invalid path
		// - Empty
		// - Contain one or more movies
		// - Contain one or more TV Shows
		// - Contain one or more movies and one or more TV Shows
	
		File inputDirectoryFile = new File( inputDirectory ) ;
		if( !inputDirectoryFile.exists() )
		{
			log.warning( "inputDirectory does not exist: " + inputDirectory ) ;
			return filesToTranscode ;
		}
	
		// First, look for files in the inputDirectory with extensions listed in transcodeExtensions
		filesToTranscode.addAll( getTranscodeFilesInDirectory( inputDirectory, transcodeExtensions ) ) ;
		return filesToTranscode ;
	}

	public boolean transcodeFile( TranscodeFile inputFile )
    {
    	// Precondition: ffmpegProbeResult is not null
    	log.info( "transcodeFile> Transcoding: " + inputFile ) ;

		// Perform the options build by these steps:
		//  1) Setup ffmpeg basic options
		//  2) Include source file
    	//  3) Include input SRT file(s)
    	//  4) Add video transcode options
		//  5) Add audio transcode options
		//  6) Add subtitle transcode options
		//  7) Add metadata transcode options
		//  8) Add output filename (.mp4)
    	
    	// ffmpegCommand will hold the command to execute ffmpeg
    	ImmutableList.Builder< String > ffmpegCommand = new ImmutableList.Builder<String>() ;
    	
    	// 1) Setup ffmpeg basic options
		ffmpegCommand.add( Common.getPathToFFmpeg() ) ;
		
		// Overwrite existing files
		ffmpegCommand.add( "-y" ) ;
		
		// 2) Include source file
		ffmpegCommand.add( "-i", inputFile.getMKVFileNameWithPath() ) ;

		// 3) Include all other input files (such as .srt, except forced subtitles)
		for( Iterator< File > fileIterator = inputFile.getSRTFileListIterator() ; fileIterator.hasNext() ; )
		{
			final File srtFile = fileIterator.next() ;
			if( !srtFile.getName().contains( forcedSubTitleFileNameContains ) )
			{
				ffmpegCommand.add( "-i", srtFile.getAbsolutePath() ) ;
			}
		}
		
		//  4) Add video transcode options
		ffmpegCommand.addAll( buildVideoTranscodeOptions( inputFile ).build() ) ;

		//  5) Add audio transcode options
		ffmpegCommand.addAll( buildAudioTranscodeOptions( inputFile ).build() ) ;
		
		//  6) Add subtitle transcode options
		ffmpegCommand.addAll( buildSubTitleTranscodeOptions( inputFile ).build() ) ;
		
		//  7) Add metadata info
		
		//  8) Add output filename (.mp4)
		ffmpegCommand.add( inputFile.getMP4OutputFileNameWithPath() ) ;

    	long startTime = System.nanoTime() ;
    	log.info( common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;

		log.info( "transcodeFile> Executing command: " + common.toStringForCommandExecution( ffmpegCommand.build() ) ) ;

    	// Only execute the transcode if testMode is false
    	boolean executeSuccess = common.getTestMode() ? true : common.executeCommand( ffmpegCommand ) ;
    	if( !executeSuccess )
    	{
    		log.info( "transcodeFile> Error in execute command" ) ;
    		// Do not move any files since the transcode failed
    		return false ;
    	}
    	
    	long endTime = System.nanoTime() ; double timeElapsedInSeconds = (endTime - startTime) / 1000000000.0 ;

    	double timePerGigaByte = timeElapsedInSeconds / (inputFile.getInputFileSize() / 1000000000.0) ;
    	log.info( "transcodeFile> Elapsed time to transcode "
    			+ inputFile.getMkvFileName()
    			+ ": "
    			+ common.getNumberFormat().format( timeElapsedInSeconds )
    			+ " seconds, "
    			+ common.getNumberFormat().format( timeElapsedInSeconds / 60.0 )
    			+ " minutes, or "
    			+ common.getNumberFormat().format( timePerGigaByte )
    			+ " seconds per GB" ) ;

    	return true ;
    }

	public String[] getAudioStreamsByNameArray() {
		return audioStreamsByNameArray;
	}

	public audioStreamTranscodeOptionsType getAudioStreamTranscodeOptions() {
		return audioStreamTranscodeOptions;
	}

	public String getAudioTranscodeLibrary() {
		return audioTranscodeLibrary;
	}

	public String getForcedSubTitleFileNameContains() {
		return forcedSubTitleFileNameContains;
	}

	public String getMkvFinalDirectory() {
		return mkvFinalDirectory;
	}

	public String getMkvInputDirectory() {
		return mkvInputDirectory;
	}

	public String getMp4FinalDirectory() {
		return mp4FinalDirectory;
	}

	public String getMp4OutputDirectory() {
		return mp4OutputDirectory;
	}

	public boolean isAddAudioStereoStream() {
		return addAudioStereoStream;
	}

	public boolean isAudioStreamOptionAudioStreamAll()
	{
		return getAudioStreamTranscodeOptions() == audioStreamTranscodeOptionsType.audioStreamAll ;
	}

	public boolean isAudioStreamOptionAudioStreamAudioStreamsByName()
	{
		return getAudioStreamTranscodeOptions() == audioStreamTranscodeOptionsType.audioStreamsByName ;
	}

	public boolean isAudioStreamOptionAudioStreamEnglishOnly()
	{
		return getAudioStreamTranscodeOptions() == audioStreamTranscodeOptionsType.audioStreamEnglishOnly ;
	}

	public boolean isAudioStreamOptionAudioStreamPrimaryPlusEnglish()
	{
		return getAudioStreamTranscodeOptions() == audioStreamTranscodeOptionsType.audioStreamPrimaryPlusEnglish ;
	}

	public boolean isDeInterlaceInput() {
		return deInterlaceInput;
	}

	public boolean isDoTranscodeVideo() {
		return doTranscodeVideo;
	}

	public void setDeInterlaceInput(boolean deInterlaceInput) {
		this.deInterlaceInput = deInterlaceInput;
	}

	public void setDoTranscodeVideo(boolean doTranscodeVideo) {
		this.doTranscodeVideo = doTranscodeVideo;
	}

	public void setMkvInputDirectory(String mkvInputDirectory) {
		this.mkvInputDirectory = mkvInputDirectory;
	}

	public void setMkvFinalDirectory(String mkvFinalDirectory) {
		this.mkvFinalDirectory = mkvFinalDirectory;
	}

	public void setMp4FinalDirectory(String mp4FinalDirectory) {
		this.mp4FinalDirectory = mp4FinalDirectory;
	}

	public void setMp4OutputDirectory(String mp4OutputDirectory) {
		this.mp4OutputDirectory = mp4OutputDirectory;
	}

}
