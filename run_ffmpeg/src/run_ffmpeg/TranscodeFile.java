package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.google.gson.Gson;

/**
 * Represent a file to be transcoded, including all of its output paths.
 * Assumption: A TV Show always has "Season " somewhere in its path.
 * Any directory variable will always have a path separator at the end.
 * @author Dan
 *
 */
public class TranscodeFile
{
	/// The following four Files represent the necessary information from which to
	/// make all decisions necessary, and provide all required information, for use
	/// with a file to be transcoded.

	/// File representing the input file
	protected File inputFile = null ;

	/// File representing the output file that is built with the transcode.
	protected File tmpOutputFile = null ;

	/// File representing the mp4 file in its final location. May be the same as the mp4OutputFile
	protected File finalOutputFile = null ;

	/// Logging stream
	private transient Logger log = null ;

	/// Common methods and variables
	private transient Common common = null ;

	/// Common transcode methods and variables
	//	private transient TranscodeCommon transcodeCommon = null ;

	// List of subtitle files corresponding to this TranscodeFile
	private List< File > srtFileList = null ;
	private List< File > supFileList = null ;
	private List< File > dvdSubTitleFileList = null ;

	/// Track the ffprobe result for this file
	/// This could technically be either an mkv or mp4 file.
	protected FFmpegProbeResult theFFmpegProbeResult = null ;

	/// The extension of a file, corresponding to this one, that indicates the file
	/// is currently being transcode
	public static final String transcodeInProgressFileExtension = ".in_work" ;

	/// The extension of a file, corresponding to this one, that indicates the file
	/// transcode is complete.
	public static final String transcodeCompleteFileExtension = ".complete" ;

	/// tvShowName is the name of the TV show or the movie name
	protected String tvShowName = "" ;

	/// tvShowSeasonName is the season of the tv show, such as "Season 04", or empty if a movie
	protected String tvShowSeasonName = "" ;

	/// The name of the movie (with (year)), or empty if tv show
	protected String movieName = "" ;

	private boolean isTVShow = false ;
	private boolean isOtherVideo = false ;

	/// Variables to track basic characteristics of the input file
	protected boolean _audioHasStereo = false ;
	protected boolean _audioHasFivePointOne = false ;
	protected boolean _audioHasSixPointOne = false ;
	protected boolean _audioHasSevenPointOne = false ;
	protected int numAudioStreams = 0 ;
	private ArrayList< FFmpegStream > audioStreams = new ArrayList< FFmpegStream >() ;

	/**
	 * mkvInputFile is the file with full path to the .mkv file. The next three are directories.
	 */
	public TranscodeFile( final File inputFile,
			final String tmpOutputDirectory,
			final String finalOutputDirectory,
			Logger log )
	{
		assert( inputFile != null ) ;
		assert( inputFile.exists() ) ;
		assert( !inputFile.isDirectory() ) ;
		assert( tmpOutputDirectory != null ) ;
		assert( finalOutputDirectory != null ) ;

		setInputFile( inputFile ) ;
		this.log = log ;
		common = new Common( log ) ;

		final String tmpOutputFileName = inputFile.getName() ;
		final String tmpOutputFileNameWithPath = common.addPathSeparatorIfNecessary( getTmpOutputDirectory() ) + tmpOutputFileName ;
		setTmpOutputFile( new File( tmpOutputFileNameWithPath ) ) ;

		final String finalOutputFileNameWithPath = common.addPathSeparatorIfNecessary( finalOutputDirectory ) + tmpOutputFileName ;
		setFinalOutputFile( new File( finalOutputFileNameWithPath ) ) ;

		buildPaths() ;
		buildSubTitleFileLists() ;
		dvdSubTitleFileList = buildSubTitleFileList( "dvd_subtitle" ) ;
	}

	public boolean audioHasFivePointOne()
	{
		return _audioHasFivePointOne;
	}

	public boolean audioHasSevenPointOne()
	{
		return _audioHasSevenPointOne;
	}

	public boolean audioHasSixPointOne()
	{
		return _audioHasSixPointOne;
	}

	public boolean audioHasStereo()
	{
		return _audioHasStereo;
	}

	private void buildPaths()
	{
		// First, extract the tv show or movie name and associated information
		if( getInputDirectory().contains( "Season " ) )
		{
			// TV Show
			//			log.fine( "Found tv show file: " + getMKVInputFileNameWithPath() ) ;
			setTVShow() ;

			setTVShowName( getInputDirectoryFile().getParentFile().getName() ) ;
			setTVShowSeasonName( getInputFile().getParentFile().getName() ) ;
		}
		else if( getInputDirectory().contains( "(" ) )
		{
			// Movie
			//			log.fine( "Found movie file: " + getMKVInputFileNameWithPath() ) ;

			// The formal should be like this:
			// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
			setMovieName( getInputDirectoryFile().getName() ) ;
			// movieName should be of the form "Transformers (2007)"
		}
		else
		{
			// Other Videos
			//			log.fine( "Found Other Videos file: " + getMKVInputFileNameWithPath() ) ;
			setOtherVideo( true ) ;

			// Treat this as a movie in most respects, except the path
			setMovieName( getInputDirectoryFile().getName() ) ;
		}
	}

	// TODO: Review this code for a better way.
	protected String buildFinalDirectoryPath( final File inputFile, String inputDirectory )
	{
		String finalDirectoryPath = inputDirectory ;

		// The transcoding could be just for a single directory or show, potentially even
		// a single season
		// Be sure to address here.
		if( isTVShow() )
		{
			// Does the original mkvFinalDirectory include the season name? ("Season 04")
			if( inputDirectory.contains( getTVShowSeasonName() ) )
			{
				// No action to update the mkvFinalDirectory
			}
			else if( inputDirectory.contains( getTVShowName() ) )
			{
				// TV show name is included, but not the season
				finalDirectoryPath += getTVShowSeasonName() + common.getPathSeparator() ;
			}
			else
			{
				// Neither tv show nor season name is included
				finalDirectoryPath += getTVShowName() + common.getPathSeparator()
				+ getTVShowSeasonName() + common.getPathSeparator() ;
			}
		}
		else
		{
			// Similar to above, this file could be part of transcoding many movies or a single movie.
			// If it's a single movie, then the target directory will have the name of the movie in the path.
			if( inputDirectory.contains( "(" ) && inputDirectory.contains( ")" ) )
			{
				// Contains both open and closing paranthesis, indicating a year, which indicates a movie
				//				out( "TranscodeFile.buildFinalDirectoryPath> Found movie in the inputDirectory" ) ;
				// Nothing to do
			}
			else
			{
				// Does not include movie directory in the path
				setMovieName( getInputDirectoryFile().getName() ) ;
				finalDirectoryPath += getMovieName() + common.getPathSeparator() ;
			}
		} // if( isTVShow() )
		return finalDirectoryPath ;
	}

	/**
	 * Build the list of SRT or SUP files associated with the stored MKV file.
	 * @param extension
	 * @return
	 */
	protected List< File > buildSubTitleFileList( final String extension )
	{
		List< File > theFileList = new ArrayList< File >() ;
		// Find files that match the mkv file name with srt or sup files
		// The mkv file name should be like: "Movie name [Unrated] (2001).mkv" where "[Unrated]" may or may
		//  not exist.
		// srt files for that file will look like: "Movie name [Unrated] (2001).#.srt" where # is
		//  a number written as digits (3 not three).
		// Also look for "Movie name [Unrated] (2001).sup" as match

		// fileNameSearchString is the wildcard/regex search string for the file name (no path)
		// Retrieve the filename and remove the extension.
		// --> Movie name [Unrated] (2001).
		final String fileNameWithoutExtension = Common.stripExtensionFromFileName( getInputFile().getName() ) ;

		// Now search for any file that starts with the fileNameWithPath and ends with .srt
		final File[] filesInDirectory = getInputDirectoryFile().listFiles() ;
		if( null == filesInDirectory )
		{
			log.warning( "Found empty directory for file: " + toString() ) ;
			return theFileList ;
		}

		for( File searchFile : filesInDirectory )
		{
			final String searchFileName = searchFile.getName() ;
			if( searchFileName.startsWith( fileNameWithoutExtension ) && searchFileName.endsWith( extension ) )
			{
				// Matches the file name with extension
				theFileList.add( searchFile ) ;
			}
		}
		return theFileList ;
	}

	public void buildSubTitleFileLists()
	{
		srtFileList = buildSubTitleFileList( "srt" ) ;
		supFileList = buildSubTitleFileList( "sup" ) ;
	}

	public Iterator< File > getAllSRTFilesIterator()
	{
		return srtFileList.iterator() ;
	}

	protected FFmpegStream getAudioStreamAt( int index )
	{
		if( (index < 0) || (index >= getNumAudioStreams()) )
		{
			return null ;
		}
		return audioStreams.get( index ) ;
	}

	/**
	 * Return an ArrayList of FFmpegStreams of audio streams that match the given
	 * input search string. The searching string will be used in a case insensitive match
	 * against the audio stream language, or * for all streams.
	 * @param languageSearch
	 * @return
	 */
	public ArrayList< FFmpegStream > getAudioStreamsByLanguage( final String languageSearch )
	{
		ArrayList< FFmpegStream > audioStreamsToReturn = new ArrayList< FFmpegStream >() ;
		for( FFmpegStream audioStream : audioStreams )
		{
			if( languageSearch.equalsIgnoreCase( "*" ) )
			{
				// Wildcard search, include all audio streams
				audioStreamsToReturn.add( audioStream ) ;
				continue ;
			}

			String audioStreamLanguage = audioStream.tags.get( "language" ) ;
			if( null == audioStreamLanguage )
			{
				log.info( "No language tag found for stream: " + audioStream.toString() ) ;
				audioStreamLanguage = "*" ;
			}

			if( languageSearch.equalsIgnoreCase( audioStreamLanguage ) )
			{
				// Found a match
				audioStreamsToReturn.add( audioStream ) ;
			}
			// else no match
		} // for( audioStream )
		return audioStreamsToReturn ;
	}

	/**
	 * Retrieve the forced subtitle file for this mkv input file. I have yet to see an instance where
	 *  a file has more than one forced subtitle stream.
	 * @return
	 */
	public File getForcedSubTitleFile()
	{
		File retMe = null ;
		for( File srtFile : srtFileList )
		{
			if( srtFile.getName().contains( TranscodeCommon.getForcedSubTitleFileNameContains() ) )
			{
				retMe = srtFile ;
				break ;
			}
		}
		return retMe ;
	}

	/**
	 * Return the zero-based index of the stream associated with the forced subtitle.
	 * The stream number is global to all streams in the file.
	 * Returns -1 if no such forced subtitle stream exists.
	 * @return
	 */
	public int getForcedSubTitleStreamNumber()
	{
		// The stream index is captured in the name of the srt file.
		// First, get the file.
		File fsbFile = getForcedSubTitleFile() ;
		if( null == fsbFile )
		{
			log.warning( "Unable to find forced subtitle file for TranscodeFile: " + toString() ) ;
			return -1 ;
		}
		// Post-condition: Found the forced subtitle file.
		//		out( "getForcedSubTitleStreamNumber> Found forced subtitle file (name): " + fsbFile.getName() ) ;
		// File name should be of the form: "file name[-extra|(year)].forced_subtitle.<num>.srt"
		// Example: Game Of Thrones - S03E04 - And Now His Watch Is Ended.forced_subtitle.12.srt
		// Grab the stream id from the second to last token
		// split() searches by regular expression
		String[] tokens = fsbFile.getName().split( "\\." ) ;
		// A properly formed file name with "forced_subtitle" should have (at least?) 4 tokens
		if( tokens.length < 4 )
		{
			log.warning( "Invalid number of tokens in file name: " + fsbFile.getName()
			+ ", expected 4 but got: " + tokens.length ) ;
			return -1 ;
		}
		// Post-condition: tokens has at least 4 tokens
		// We want the second from last
		String streamNumberString = tokens[ 2 ] ;
		Integer streamNumberInteger = Integer.parseInt( streamNumberString ) ;

		return streamNumberInteger.intValue() ;
	}

	public long getInputFileSize()
	{
		return getInputFile().length() ;
	}

	public String getMetaDataTitle()
	{
		String metaDataTitle = "" ;

		// Remove the extension
		String fileNameWithoutExtension = Common.stripExtensionFromFileName( getInputFile().getName() ) ;
				
		StringTokenizer tokens = new StringTokenizer( fileNameWithoutExtension, "-" ) ;

		// 0 tokens means it is a movie
		// Example: Transformers (2007)
		if( 1 == tokens.countTokens() )
		{
			metaDataTitle += tokens.nextToken().trim() ;
		}
		// 2 tokens means it is a movie/extra with an extra type
		// Keep the movie/extra name
		// Example: Making of Transformers-behindthescenes
		else if( 2 == tokens.countTokens() )
		{
			metaDataTitle += tokens.nextToken().trim() ;
		}
		// 3 or 4 tokens means it is a TV show
		// Example: Star Trek the Next Generation - s02e100 - Mission Overview Year 2
		// Example: The Scooby-Doo Show - s01e01 - Description (3 hyphens == 4 tokens)
		// Keep the third token (Mission Overview Year 2)
		else
		{
			// At least 3 tokens, maybe more
			// Get the last token
			String description = "Error DescriptionWithToken" ;
			while( tokens.hasMoreTokens() )
			{
				description = tokens.nextToken().trim() ;
			}

			metaDataTitle += description ;
		}
		return metaDataTitle ;
	}

	public String getInputDirectory()
	{
		return getInputFile().getParent() ;
	}

	public File getInputDirectoryFile()
	{
		return getInputFile().getParentFile() ;
	}

	protected File getInputFile()
	{
		return inputFile;
	}

	public String getInputFileNameWithPath()
	{
		return getInputFile().getAbsolutePath() ;
	}

	public String getMovieName()
	{
		return movieName ;
	}

	public String getTmpOutputFileName()
	{
		return getFinalOutputFile().getName() ;
	}

	public String getFinalOutputDirectory()
	{
		return getFinalOutputFile().getParent() ;
	}

	public String getFinalOutputDirectoryFile()
	{
		return getFinalOutputFile().getParent() ;
	}

	protected File getFinalOutputFile()
	{
		return finalOutputFile ;
	}

	public String getFinalOutputFileNameWithPath()
	{
		return getFinalOutputFile().getAbsolutePath() ;
	}

	public String getTmpOutputDirectory()
	{
		return getTmpOutputFile().getParent() ;
	}

	public File getTmpOutputDirectoryFile()
	{
		return getTmpOutputFile().getParentFile() ;
	}

	protected File getTmpOutputFile()
	{
		return tmpOutputFile;
	}

	public String getTmpOutputFileNameWithPath()
	{
		return getTmpOutputFile().getAbsolutePath() ;
	}

	public int getNumAudioStreams()
	{
		return audioStreams.size() ;
	}

	public String getPrimaryAudioLanguage()
	{
		// The audio streams should already be in numerical order, low to high
		// NOTE: This assumes the primary language will be the first audio stream,
		//  although this is not guaranteed.
		if( audioStreams.isEmpty() )
		{
			log.warning( "Empty audio stream list" ) ;
			return "EMPTY" ;
		}
		final String primaryAudioLanguage = audioStreams.get( 0 ).tags.get( "language" ) ;
		if( null == primaryAudioLanguage )
		{
			// First audio stream has no language
			log.info( "Empty language for first audio stream" ) ;
			return "UNKNOWN" ;
		}
		return primaryAudioLanguage ;
	}

	public File getRealSRTFile( int index )
	{
		if( isSRTFileListEmpty() ) return null ;
		if( index < 0 ) return null ;
		if( index >= srtFileList.size() ) return null ;
		File returnFile = srtFileList.get( index ) ;
		return returnFile ;
	}

	public Iterator< File > getSRTFileListIterator()
	{
		return srtFileList.iterator() ;
	}

	public boolean getTranscodeStatus( final String extensionToCheck )
	{
		final String transcodeExtensionFileName = Common.replaceExtension( getInputFileNameWithPath(), extensionToCheck ) ;
		if( common.fileExists( transcodeExtensionFileName ))
		{
			log.fine( extensionToCheck + "> Found file " + transcodeExtensionFileName ) ;
			return true ;
		}
		return false ;
	}

	public String getTVShowName()
	{
		return tvShowName;
	}

	public String getTVShowSeasonName()
	{
		return tvShowSeasonName;
	}

	public boolean hasDVDSubTitleInputFile()
	{
		return !dvdSubTitleFileList.isEmpty() ;
	}

	public boolean hasForcedSubTitleFile()
	{
		for( File srtFile : srtFileList )
		{
			if( srtFile.getName().contains( TranscodeCommon.getForcedSubTitleFileNameContains() ) )
			{
				return true ;
			}
		}
		return false ;
	}

	public boolean hasSRTInputFiles()
	{
		return !srtFileList.isEmpty() ;
	}

	public boolean hasSUPInputFiles()
	{
		return !supFileList.isEmpty() ;
	}

	public boolean isOtherVideo()
	{
		return isOtherVideo;
	}

	public boolean isSRTFileListEmpty()
	{
		return srtFileList.isEmpty() ;
	}

	public boolean isTranscodeComplete()
	{
		return getTranscodeStatus( transcodeCompleteFileExtension ) ;
	}

	public boolean isTranscodeInProgress()
	{
		return getTranscodeStatus( transcodeInProgressFileExtension ) ;
	}

	public boolean isTVShow()
	{
		return isTVShow ;
	}

	/**
	 * Make the directories for the various output files.
	 * This honors the common.testMode configuration variable.
	 */
	public void makeDirectories()
	{
		common.makeDirectory( getTmpOutputDirectory() ) ;
		common.makeDirectory( getFinalOutputDirectory() ) ;
	}

	protected void processAudioStreams( List< FFmpegStream > inputStreams )
	{
		// Pre-condition: inputStreams contains streams only of type audio
		audioStreams.addAll( inputStreams ) ;

		for( FFmpegStream theInputStream : inputStreams )
		{
			if( null == theInputStream.channel_layout )
			{
				// No channel_layout
				// This is not a big deal -- many extras videos lack the channel_layout field 
				//				log.fine( "No channel_layout field found for file: " + toString() + " for file " + getMKVInputFile().getAbsolutePath() ) ;
			}
			else if( theInputStream.channel_layout.contains( "stereo" )
					|| theInputStream.channel_layout.contains( "2 channels" ) )
			{
				setAudioHasStereo( true ) ;
			}
			else if( theInputStream.channel_layout.contains( "5.1" ) )
			{
				setAudioHasFivePointOne( true ) ;
			}
			else if( theInputStream.channel_layout.contains( "6.1" ) )
			{
				setAudioHasSixPointOne( true ) ;
			}
			else if( theInputStream.channel_layout.contains( "7.1" ) )
			{
				setAudioHasSevenPointOne( true ) ;
			}
			else if( theInputStream.channel_layout.contains( "5.0(side)" ) )
			{
				// TODO
			}
			else if( theInputStream.channel_layout.contains( "4.0" ) )
			{
				// TODO
			}
			else if( theInputStream.channel_layout.contains( "4.1" ) )
			{

			}
			else if( theInputStream.channel_layout.contains( "mono" ) )
			{

			}
			else
			{
				log.warning( "Unknown channel_layout: " + theInputStream.channel_layout + " for file " + getInputFile().getAbsolutePath() ) ;
			}
		}
	}

	public void processFFmpegProbeResult( FFmpegProbeResult _theFFmpegProbeResult )
	{
		theFFmpegProbeResult = _theFFmpegProbeResult ;
		processVideoStreams( theFFmpegProbeResult.getStreamsByCodecType( "video" ) ) ;
		processAudioStreams( theFFmpegProbeResult.getStreamsByCodecType( "audio" ) ) ;
		processSubTitleStreams( theFFmpegProbeResult.getStreamsByCodecType( "subtitle" ) ) ;
	}

	protected void processVideoStreams( List< FFmpegStream > inputStreams )
	{
		// Pre-condition: inputStreams contains streams only of type video
		//		for( FFmpegStream theInputStream : inputStreams )
		//		{
		//			
		//		}
	}

	protected void processSubTitleStreams( List< FFmpegStream > inputStreams )
	{
		// Pre-condition: inputStreams contains streams only of type subtitle
		//		for( FFmpegStream theInputStream : inputStreams )
		//		{
		//			
		//		}
	}

	public void setAudioHasFivePointOne(boolean _audioHasFivePointOne)
	{
		this._audioHasFivePointOne = _audioHasFivePointOne;
	}

	public void setAudioHasSixPointOne(boolean _audioHasSixPointOne)
	{
		this._audioHasSixPointOne = _audioHasSixPointOne;
	}

	public void setAudioHasSevenPointOne(boolean _audioHasSevenPointOne)
	{
		this._audioHasSevenPointOne = _audioHasSevenPointOne;
	}

	public void setAudioHasStereo(boolean _audioHasStereo) {
		this._audioHasStereo = _audioHasStereo;
	}

	protected void setFinalOutputFile( File finalOutputFile )
	{
		this.finalOutputFile = finalOutputFile ;
	}

	protected void setInputFile( File inputFile )
	{
		this.inputFile = inputFile ;
	}

	public void setMovieName( final String movieName )
	{
		this.movieName = movieName ;
	}

	protected void setTmpOutputFile( File tmpOutputFile )
	{
		this.tmpOutputFile = tmpOutputFile ;
	}

	public void setOtherVideo(boolean isOtherVideo)
	{
		this.isOtherVideo = isOtherVideo ;
	}

	public void setTranscodeComplete()
	{
		setTranscodeStatus( transcodeCompleteFileExtension ) ;
	}

	public void setTranscodeInProgress()
	{
		setTranscodeStatus( transcodeInProgressFileExtension ) ;
	}

	public void setTranscodeStatus( final String extensionToWrite )
	{
		final String mkvTouchFileName = Common.replaceExtension( getInputFileNameWithPath(), extensionToWrite ) ;
		log.fine( extensionToWrite + "> Touching file: " + mkvTouchFileName ) ;
		if( !common.getTestMode() )
		{
			common.touchFile( mkvTouchFileName ) ;
		}
	}

	public void setTVShow()
	{
		isTVShow = true ;
	}

	public void setTVShowName( String tvShowName )
	{
		this.tvShowName = tvShowName ;
	}

	public void setTVShowSeasonName(String tvShowSeasonName)
	{
		this.tvShowSeasonName = tvShowSeasonName ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}

	public void unSetTranscodeComplete()
	{
		unSetTranscodeStatus( transcodeCompleteFileExtension ) ;
	}

	public void unSetTranscodeInProgress()
	{
		unSetTranscodeStatus( transcodeInProgressFileExtension ) ;
	}

	public void unSetTranscodeStatus( final String extensionToWrite )
	{
		final String touchFileName = Common.replaceExtension( getInputFileNameWithPath(), extensionToWrite ) ;
		log.info( extensionToWrite + "> Deleting file: " + touchFileName ) ;
		if( !common.getTestMode() )
		{
			File fileToDelete = new File( touchFileName ) ;
			fileToDelete.delete() ;
		}
	}

	public FFmpegProbeResult getTheFFmpegProbeResult()
	{
		return theFFmpegProbeResult ;
	}
	
	public int numSRTFiles()
	{
		return srtFileList.size() ;
	}

	public String getInputFileName()
	{
		return getInputFile().getName() ;
	}
}
