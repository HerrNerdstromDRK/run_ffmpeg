package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

/**
 * Represent a file to be transcoded, including all of its output paths.
 * Assumption: A TV Show always has "Season " somewhere in its path.
 * Any directory variable will always have a path separator at the end.
 * @author Dan
 *
 */
public class TranscodeFile
{
	/// theFile is the input file, with extension that can vary
	/// The output file will always be .mp4
	protected File theMKVFile = null ;
	protected String theMP4FileName = null ;
	protected String mkvFinalDirectory = null ;
	protected String mp4OutputDirectory = null ;
	protected String mp4FinalDirectory = null ;
	private transient Logger log = null ;
	private transient Common common = null ;
	private transient TranscodeCommon transcodeCommon = null ;
	
	// List of subtitle files corresponding to this TranscodeFile
	private List< File > srtFileList = null ;
	private List< File > supFileList = null ;
	private List< File > dvdSubTitleFileList = null ;
	
	// Track the ffprobe result for this input file
	protected FFmpegProbeResult theFFmpegProbeResult = null ;

	/// The extension of a file, corresponding to this one, that indicates the file
	/// is currently being transcode
	public static final String transcodeInProgressFileExtension = ".in_work" ;
	
	/// The extension of a file, corresponding to this one, that indicates the file
	/// transcode is complete.
	public static final String transcodeCompleteFileExtension = ".complete" ;
	
	/// tvShowName will be the name of the TV show or the movie name
	protected String tvShowName = "" ;
	/// tvShowSeasonName will be the season of the tv show, such as "Season 04", or empty if a movie
	protected String tvShowSeasonName = "" ;
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

	public TranscodeFile( final File theMKVFile,
			final String mkvFinalDirectory,
			final String mp4OutputDirectory,
			final String mp4FinalDirectory,
			Logger log,
			TranscodeCommon transcodeCommon )
	{
		assert( theMKVFile != null ) ;
		assert( theMKVFile.exists() ) ;
		assert( !theMKVFile.isDirectory() ) ;
		assert( mkvFinalDirectory != null ) ;
		assert( mp4OutputDirectory != null ) ;
		assert( mp4FinalDirectory != null ) ;

		this.theMKVFile = theMKVFile ;
		this.log = log ;
		common = new Common( log ) ;
		this.mkvFinalDirectory = common.addPathSeparatorIfNecessary( mkvFinalDirectory ) ;
		this.mp4OutputDirectory = common.addPathSeparatorIfNecessary( mp4OutputDirectory ) ;
		this.mp4FinalDirectory = common.addPathSeparatorIfNecessary( mp4FinalDirectory ) ;
		this.transcodeCommon = transcodeCommon ;
		
		buildPaths() ;
		srtFileList = buildSubTitleFileList( "srt" ) ;
		supFileList = buildSubTitleFileList( "sup" ) ;
		dvdSubTitleFileList = buildSubTitleFileList( "dvd_subtitle" ) ;
	}

	private void buildPaths()
	{
		// The input file could have any of the input file extensions
		// Just walk through each here, replacing it with mp4
		// Note that this is safe since the run_ffmpeg.transcodeExtensions
		// array includes the periods (".mkv", ".MOV", etc)
		for( String theExtension : transcodeCommon.getTranscodeExtensions() )
		{
			if( theMKVFile.getName().contains( theExtension ) )
			{
				setTheMP4FileName( theMKVFile.getName().replace( theExtension, ".mp4" ).replace( "7.1 ", "" ).replace( "6.1 ", "" ) ) ;
				break ;
			}
		}

		// First, extract the tv show or movie name and associated information
		if( theMKVFile.getParent().contains( "Season " ) )
		{
			// TV Show
			log.fine( "Found tv show file: " + getTheMKVFile().toString() ) ;
			setTVShow() ;

			setTvShowName( getTheMKVFile().getParentFile().getParentFile().getName() ) ;
			setTvShowSeasonName( getTheMKVFile().getParentFile().getName() ) ;
		}
		else if( theMKVFile.getParent().contains( "(" ) )
		{
			// Movie
			log.fine( "Found movie file: " + getTheMKVFile().toString() ) ;

			// The formal should be like this:
			// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
			setMovieName( getTheMKVFile().getParentFile().getName() ) ;
			// movieName should be of the form "Transformers (2007)"
		}
		else
		{
			// Other Videos
			log.fine( "Found Other Videos file: " + getTheMKVFile().toString() ) ;
			setOtherVideo( true ) ;

			// Treat this as a movie in most respects, except the path
			setMovieName( getTheMKVFile().getParentFile().getName() ) ;
		}

		if( isOtherVideo() )
		{
			setMkvFinalDirectory( getMkvFinalDirectory() ) ;
			setMp4OutputDirectory( getMp4OutputDirectory() ) ;
			setMp4FinalDirectory( getMp4FinalDirectory() ) ;
		}
		else
		{
			// TV Show or Movie
			setMkvFinalDirectory( buildFinalDirectoryPath( getTheMKVFile(), getMkvFinalDirectory() ) ) ;
			setMp4OutputDirectory( buildFinalDirectoryPath( getTheMKVFile(), getMp4OutputDirectory() ) ) ;
			setMp4FinalDirectory( buildFinalDirectoryPath( getTheMKVFile(), getMp4FinalDirectory() ) ) ;
		}
	}

	protected List< File > buildSubTitleFileList( final String extension )
	{
		// Find files that match the mkv file name with srt or sup files
		// the mkv file name should be like: "Movie name [Unrated] (2001).mkv" where "[Unrated]" may or may
		//  not exist.
		// srt files for that file will look like: "Movie name [Unrated] (2001).#.srt" where # is
		//  a number written as digits (3 not three).
		// Also look for "Movie name [Unrated] (2001).sup" as match
		
	   	// fileNameSearchString is the wildcard/regex search string for the file name (no path)
		// Retrieve the filename and remove the extension.
		// --> Movie name [Unrated] (2001).
    	String fileNameSearchRegex = getTheMKVFile().getName() ;
    	fileNameSearchRegex = fileNameSearchRegex.substring( 0, fileNameSearchRegex.lastIndexOf( '.' ) ) ;
    	
    	// Add the trailing period after the file name as a literal
    	fileNameSearchRegex = fileNameSearchRegex + "\\." ;
    	
    	// Replace special regex characters with their literal equivalents
    	fileNameSearchRegex = fileNameSearchRegex.replace( "(", "\\(" ).replace( ")", "\\)" ) ;
    	fileNameSearchRegex = fileNameSearchRegex.replace( "[", "\\[" ).replace( "]", "\\]" ) ;
    	
    	// Add search parameter for digit character class
    	fileNameSearchRegex += "[0-9]+\\." + extension ;

    	// searchDirectory is the directory in which to search for files that match the fileNameSearchString
    	String searchDirectory = getTheMKVFile().getParent() ;

    	// Now search for any file that starts with the fileNameWithPath and ends with .srt
    	File[] filesInDirectory = (new File( searchDirectory )).listFiles() ;
    	List< File > theFileList = new ArrayList< File >() ;
    	for( File searchFile : filesInDirectory )
    	{
    		String searchFileName = searchFile.getName() ;
    		if( searchFileName.matches( fileNameSearchRegex ) )
    		{
    			// Found a matching subtitle file
    			log.fine( "searchFile (" + searchFile.getName() + ") matches regex: " + fileNameSearchRegex ) ;
    			theFileList.add( searchFile ) ;
    		}
    	}
    	return theFileList ;
	}
	
	protected String buildFinalDirectoryPath( final File inputFile, String inputDirectory )
	{
		String finalDirectoryPath = inputDirectory ;

		// The transcoding could be just for a single directory or show, potentially even
		// a single season
		// Be sure to address here.
		if( isTVShow() )
		{
			// Does the original mkvFinalDirectory include the season name? ("Season 04")
			if( inputDirectory.contains( getTvShowSeasonName() ) )
			{
				// No action to update the mkvFinalDirectory
			}
			else if( inputDirectory.contains( getTvShowName() ) )
			{
				// TV show name is included, but not the season
				finalDirectoryPath += getTvShowSeasonName() + common.getPathSeparator() ;
			}
			else
			{
				// Neither tv show nor season name is included
				finalDirectoryPath += getTvShowName() + common.getPathSeparator()
				+ getTvShowSeasonName() + common.getPathSeparator() ;
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
				setMovieName( getTheMKVFile().getParentFile().getName().replace( "7.1 ", "" ).replace( "6.1 ", "" ) ) ;
				finalDirectoryPath += getMovieName() + common.getPathSeparator() ;
			}
		} // if( isTVShow() )
		return finalDirectoryPath ;
	}

	public String getMetaDataTitle()
	{
		String metaDataTitle = "" ;

		// Remove the extension
		String fileNameWithoutExtension = getTheMP4FileName().replace( ".mp4", "" ) ;

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

	public void makeDirectories()
	{
		common.makeDirectory( getMkvFinalDirectory() ) ;
		common.makeDirectory( getMp4OutputDirectory() ) ;
		common.makeDirectory( getMp4FinalDirectory() ) ;
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
	
	protected void processAudioStreams( List< FFmpegStream > inputStreams )
	{
		// Pre-condition: inputStreams contains streams only of type audio
		audioStreams.addAll( inputStreams ) ;
				
		for( FFmpegStream theInputStream : inputStreams )
		{
			if( null == theInputStream.channel_layout )
			{
				// No channel_layout
				log.info( "processaudioStreams> No channel_layout field found for file: " + toString() ) ;
			}
			else if( theInputStream.channel_layout.contains( "stereo" ) )
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
			else
			{
				log.warning( "TranscodeFile.processAudioStreams> Unknown channel_layout: " + theInputStream.channel_layout ) ;
			}
		}
	}
	
	protected void processSubTitleStreams( List< FFmpegStream > inputStreams )
		{
			// Pre-condition: inputStreams contains streams only of type subtitle
	//		for( FFmpegStream theInputStream : inputStreams )
	//		{
	//			
	//		}
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
	
	public File getForcedSubTitleFile()
	{
		File retMe = null ;
		for( File srtFile : srtFileList )
		{
			if( srtFile.getName().contains( transcodeCommon.getForcedSubTitleFileNameContains() ) )
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
		return getTheMKVFile().length() ;
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
	
	public String getMKVFileNameWithPath()
	{
		return getTheMKVFile().getAbsolutePath() ;
	}

	public boolean getMKVFileShouldMove()
	{
		// The MKV file should move if it's not currently in the MKVFinalDirectory
		return (!getMKVFileNameWithPath().contains( getMkvFinalDirectory() )) ;
	}

	public boolean getMP4FileShouldMove()
	{
		// The MP4 file should move if the output directory is different thank the final directory
		return (!getMp4OutputDirectory().equalsIgnoreCase( getMp4FinalDirectory() )) ;
	}

	public String getMP4FinalOutputFileNameWithPath()
	{
		return (getMp4FinalDirectory() + getTheMP4FileName()) ;
	}

	public String getMP4OutputFileNameWithPath()
	{
		return (getMp4OutputDirectory() + getTheMP4FileName()) ;
	}

	public boolean getTranscodeStatus( final String extensionToCheck )
	{
		final String transcodeExtensionFileName = Common.replaceExtension( getMKVFileNameWithPath(), extensionToCheck ) ;
		if( common.fileExists( transcodeExtensionFileName ))
		{
			log.fine( extensionToCheck + "> Found file " + transcodeExtensionFileName ) ;
			return true ;
		}
	return false ;
	}

	public boolean hasForcedSubTitleFile()
	{
		for( File srtFile : srtFileList )
		{
			if( srtFile.getName().contains( transcodeCommon.getForcedSubTitleFileNameContains() ) )
			{
				return true ;
			}
		}
		return false ;
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
		final String mkvTouchFileName = Common.replaceExtension( getMKVFileNameWithPath(), extensionToWrite ) ;
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
		final String mkvTouchFileName = Common.replaceExtension( getMKVFileNameWithPath(), extensionToWrite ) ;
		log.info( extensionToWrite + "> Deleting file: " + mkvTouchFileName ) ;
		if( !common.getTestMode() )
		{
			File fileToDelete = new File( mkvTouchFileName ) ;
			fileToDelete.delete() ;
		}
	}
	
	protected File getTheMKVFile() {
		return theMKVFile;
	}

	protected void setTheMKVFile(File theMKVFile) {
		this.theMKVFile = theMKVFile;
	}

	public String getTheMP4FileName() {
		return theMP4FileName;
	}

	public void setTheMP4FileName(String theMP4FileName) {
		this.theMP4FileName = theMP4FileName;
	}

	public String getMkvFinalDirectory() {
		return mkvFinalDirectory;
	}

	public void setMkvFinalDirectory(String mkvFinalDirectory) {
		this.mkvFinalDirectory = mkvFinalDirectory;
	}

	public String getMp4OutputDirectory() {
		return mp4OutputDirectory;
	}

	public void setMp4OutputDirectory(String mp4OutputDirectory) {
		this.mp4OutputDirectory = mp4OutputDirectory;
	}

	public String getMp4OutputFileNameWithPath()
	{
		return (getMp4OutputDirectory() + getTheMP4FileName()) ;
	}

	public String getMp4FinalDirectory() {
		return mp4FinalDirectory;
	}

	public void setMp4FinalDirectory(String mp4FinalDirectory) {
		this.mp4FinalDirectory = mp4FinalDirectory;
	}

	public String getMkvFileName()
	{
		return getTheMKVFile().getName() ;
	}

	public String getMkvFinalFileNameWithPath()
	{
		return getMkvFinalDirectory() + getMkvFileName() ;
	}

	public String getTvShowName() {
		return tvShowName;
	}

	public void setTvShowName(String tvShowName) {
		this.tvShowName = tvShowName;
	}

	public String getTvShowSeasonName() {
		return tvShowSeasonName;
	}

	public void setTvShowSeasonName(String tvShowSeasonName) {
		this.tvShowSeasonName = tvShowSeasonName;
	}

	public String getMovieName() {
		return movieName;
	}

	public void setMovieName(String movieName) {
		this.movieName = movieName;
	}

	@Override
	public String toString()
	{
		String retMe = "TranscodeFile> theMP4FileName: "
				+ getTheMP4FileName()
				+ ", mkvFinalDirectory: "
				+ getMkvFinalDirectory()
				+ ", mp4OutputDirectory: "
				+ getMp4OutputDirectory()
				+ ", mp4FinalDirectory: "
				+ getMp4FinalDirectory()
				+ ", length: "
				+ getInputFileSize() ;
		return retMe ;
	}

	public boolean isOtherVideo() {
		return isOtherVideo;
	}

	public void setOtherVideo(boolean isOtherVideo) {
		this.isOtherVideo = isOtherVideo;
	}

	public String getMKVInputFileName() {
		return getTheMKVFile().getAbsolutePath() ;
	}

	public String getMKVInputPath() {
		return getTheMKVFile().getParent() ;
	}
	
	public boolean audioHasStereo() {
		return _audioHasStereo;
	}

	public void setAudioHasStereo(boolean _audioHasStereo) {
		this._audioHasStereo = _audioHasStereo;
	}

	public boolean audioHasFivePointOne() {
		return _audioHasFivePointOne;
	}

	public void setAudioHasFivePointOne(boolean _audioHasFivePointOne) {
		this._audioHasFivePointOne = _audioHasFivePointOne;
	}

	public boolean audioHasSixPointOne() {
		return _audioHasSixPointOne;
	}

	public void setAudioHasSixPointOne(boolean _audioHasSixPointOne) {
		this._audioHasSixPointOne = _audioHasSixPointOne;
	}

	public boolean audioHasSevenPointOne() {
		return _audioHasSevenPointOne;
	}

	public void setAudioHasSevenPointOne(boolean _audioHasSevenPointOne) {
		this._audioHasSevenPointOne = _audioHasSevenPointOne;
	}

	public boolean hasSRTInputFiles()
	{
		return !srtFileList.isEmpty() ;
	}

	public boolean hasSUPInputFiles()
	{
		return !supFileList.isEmpty() ;
	}
	
	public boolean hasDVDSubTitleInputFile()
	{
		return !dvdSubTitleFileList.isEmpty() ;
	}
	
	public int numSRTInputFiles()
	{
		return srtFileList.size() ;
	}
	
	public boolean isSRTFileListEmpty()
	{
		return srtFileList.isEmpty() ;
	}
	
	public Iterator< File > getSRTFileListIterator()
	{
		return srtFileList.iterator() ;
	}
	
	public File getSRTFile( int index )
	{
		if( isSRTFileListEmpty() ) return null ;
		if( index < 0 ) return null ;
		if( index >= srtFileList.size() ) return null ;
		File returnFile = srtFileList.get( index ) ;
		return returnFile ;
	}
	
	public int getNumAudioStreams() {
		return audioStreams.size() ;
	}
	
}
