package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

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
	
	// List of .srt files corresponding to this TranscodeFile
	protected ArrayList< File > srtFileList = new ArrayList< File >() ;

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

	public TranscodeFile( final File theMKVFile,
			final String mkvFinalDirectory,
			final String mp4OutputDirectory,
			final String mp4FinalDirectory )
	{
		assert( theMKVFile != null ) ;
		assert( theMKVFile.exists() ) ;
		assert( !theMKVFile.isDirectory() ) ;
		assert( mkvFinalDirectory != null ) ;
		assert( mp4OutputDirectory != null ) ;
		assert( mp4FinalDirectory != null ) ;

		this.theMKVFile = theMKVFile ;
		this.mkvFinalDirectory = run_ffmpeg.addPathSeparatorIfNecessary( mkvFinalDirectory ) ;
		this.mp4OutputDirectory = run_ffmpeg.addPathSeparatorIfNecessary( mp4OutputDirectory ) ;
		this.mp4FinalDirectory = run_ffmpeg.addPathSeparatorIfNecessary( mp4FinalDirectory ) ;

		buildPaths() ;
		buildSRTFileList() ;
	}

	private void buildPaths()
	{
		// The input file could have any of the input file extensions
		// Just walk through each here, replacing it with mp4
		// Note that this is safe since the run_ffmpeg.transcodeExtensions
		// array includes the periods (".mkv", ".MOV", etc)
		for( String theExtension : run_ffmpeg.transcodeExtensions )
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
			out( "TranscodeFile.buildPaths> Found tv show file: " + getTheMKVFile().toString() ) ;
			setTVShow() ;

			setTvShowName( getTheMKVFile().getParentFile().getParentFile().getName() ) ;
			setTvShowSeasonName( getTheMKVFile().getParentFile().getName() ) ;
		}
		else if( theMKVFile.getParent().contains( "(" ) )
		{
			// Movie
			out( "TranscodeFile.buildPaths> Found movie file: " + getTheMKVFile().toString() ) ;

			// The formal should be like this:
			// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
			setMovieName( getTheMKVFile().getParentFile().getName() ) ;
			// movieName should be of the form "Transformers (2007)"
		}
		else
		{
			// Other Videos
			out( "TranscodeFile.buildPaths> Found Other Videos file: " + getTheMKVFile().toString() ) ;
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

	protected void buildSRTFileList()
	{
	   	// fileNameSearchString is the wildcard/regex search string for the file name (no path)
    	String fileNameSearchString = getTheMKVFile().getName() ;
    	fileNameSearchString = fileNameSearchString.substring( 0, fileNameSearchString.lastIndexOf( '.' ) ) ;
    	
    	// Replace any ( or ) as those seem to confuse the regex; this is probably the wrong way
    	// handle this problem, but hopefully it works.
    	fileNameSearchString = fileNameSearchString.replace( "(", "" ).replace( ")", "" ) ;
    	fileNameSearchString += "(.*).srt" ;

    	// searchDirectory is the directory in which to search for files that match the fileNameSearchString
    	String searchDirectory = getTheMKVFile().getParent() ;

    	// Now search for any file that starts with the fileNameWithPath and ends with .srt
    	File[] filesInDirectory = (new File( searchDirectory )).listFiles();
    	for( File searchFile : filesInDirectory )
    	{
    		String searchFileName = searchFile.getName().replace( "(", "" ).replace( ")", "" ) ;
    		if( searchFileName.matches( fileNameSearchString ) )
    		{
    			// Found a matching .srt file
//    			run_ffmpeg.log( "buildSRTFileList> searchFile (" + searchFile.getName() + ") matches regex: " + fileNameSearchString ) ;
    			srtFileList.add( searchFile ) ;
    		}
    	}
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
				finalDirectoryPath += getTvShowSeasonName() + getPathSeparator() ;
			}
			else
			{
				// Neither tv show nor season name is included
				finalDirectoryPath += getTvShowName() + getPathSeparator()
				+ getTvShowSeasonName() + getPathSeparator() ;
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
				finalDirectoryPath += getMovieName() + getPathSeparator() ;
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
		run_ffmpeg.makeDirectory( getMkvFinalDirectory() ) ;
		run_ffmpeg.makeDirectory( getMp4OutputDirectory() ) ;
		run_ffmpeg.makeDirectory( getMp4FinalDirectory() ) ;
	}

	public String getMKVFileNameWithPath()
	{
		return getTheMKVFile().getAbsolutePath() ;
	}

	public String getMP4OutputFileNameWithPath()
	{
		return (getMp4OutputDirectory() + getTheMP4FileName()) ;
	}

	public String getMP4FinalOutputFileNameWithPath()
	{
		return (getMp4FinalDirectory() + getTheMP4FileName()) ;
	}

	public long getInputFileSize()
	{
		return getTheMKVFile().length() ;
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

	public static String getPathSeparator()
	{
		return run_ffmpeg.getPathSeparator() ;
	}

	public static void out( final String writeMe )
	{
		run_ffmpeg.out( writeMe ) ;
	}

	public void setTVShow()
	{
		isTVShow = true ;
	}

	public boolean isTVShow()
	{
		return isTVShow ;
	}

	public boolean isTranscodeInProgress()
	{
		return getTranscodeStatus( transcodeInProgressFileExtension ) ;
	}
	
	public boolean isTranscodeComplete()
	{
		return getTranscodeStatus( transcodeCompleteFileExtension ) ;
	}
	
	public void setTranscodeInProgress()
	{
		setTranscodeStatus( transcodeInProgressFileExtension ) ;
	}
	
	public void setTranscodeComplete()
	{
		setTranscodeStatus( transcodeCompleteFileExtension ) ;
	}
	
	public boolean getTranscodeStatus( final String extensionToCheck )
	{
		String transcodeCompleteFileName = getMKVFileNameWithPath().replace( ".mkv", extensionToCheck ) ;
		if( run_ffmpeg.fileExists( transcodeCompleteFileName ))
		{
			out( "getTranscodeStatus(" + extensionToCheck + ")> Found file " + transcodeCompleteFileName ) ;
			return true ;
		}
		transcodeCompleteFileName = getMp4OutputFileNameWithPath().replace( ".mkv", extensionToCheck ) ;
		if( run_ffmpeg.fileExists( transcodeCompleteFileName ) )
		{
			out( "getTranscodeStatus(" + extensionToCheck + ")> Found file " + transcodeCompleteFileName ) ;
			return true ;
		}
		return false ;
	}
	
	public void setTranscodeStatus( final String extensionToWrite )
	{
		final String mkvTouchFileName = getMKVFileNameWithPath().replace( ".mkv", extensionToWrite ) ;
		out( "setTranscodeStatus(" + extensionToWrite + ")> Touching file: " + mkvTouchFileName ) ;
		if( !run_ffmpeg.testMode )
		{
			run_ffmpeg.touchFile( mkvTouchFileName ) ;
		}
		if( !mkvFinalDirectory.equalsIgnoreCase( mp4FinalDirectory ) )
		{
			final String mp4TouchFileName = getMP4OutputFileNameWithPath().replace( ".mp4", extensionToWrite ) ;
			out( "setTranscodeStatus(" + extensionToWrite + ")> Touching file: " + mp4TouchFileName ) ;
			if( !run_ffmpeg.testMode )
			{
				run_ffmpeg.touchFile( mp4TouchFileName ) ;
			}
		}
		// As a general rule, don't delete any files. It's no big deal if the .in_work file remains
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

}
