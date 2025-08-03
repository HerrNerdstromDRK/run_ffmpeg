package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

public class FileNamePattern
{
	protected transient File inputFile = null ;
	protected transient Logger log = null ;
	protected static final transient String logFileName = "log_filenamepattern.txt" ;
	
	protected boolean isTVShow = false ;
	protected boolean isMovie = false ;
	protected boolean isMovieExtra = false ;
	protected boolean isOtherVideo = false ;
	
	protected String extension = null ;
	protected String showName = null ;
	protected int seasonNumber = -1 ;
	protected int episodeNumber = -1 ;
	protected String episodeName = null ;
	
	protected String movieName = null ;
	protected String extraName = null ;
	protected String extraType = null ;
	
	protected String directoryName = null ;
	protected String otherVideoName = null ;

	public FileNamePattern( Logger log, final File inputFile )
	{
		this.log = log ;
		this.inputFile = inputFile ;
		parsePattern() ;
	}
	
	public static void main( String[] args )
	{
		Logger log = Common.setupLogger( logFileName, "FileNamePattern" ) ;
		
		// Used for testing.
		final String[] inputFileNamesWithPaths = {
				"\\\\skywalker\\Media\\Movies\\Zero Dark Thirty (2012)\\Targeting Jessica Chastain-behindthescenes.mkv",
				"\\\\skywalker\\Media\\Movies\\Zero Dark Thirty (2012)\\Zero Dark Thirty (2012).mkv",
				"\\\\skywalker\\Media\\To_OCR\\Boardwalk Empire (2010) {imdb-0979432}\\Season 02\\BOARDWALK_EMPIRE_S2_DISC1-D3_t04.mkv",
				"\\\\skywalker\\Media\\TV_Shows\\24 (2001) {tvdb-76290}\\Season 03\\24 - S03E03 - Day 3 3PM 4PM.mkv",
				"\\\\skywalker\\Media\\TV_Shows\\The Expanse (2015) {tvdb-280619}\\Season 04\\The Expanse - S04E112 - A New World On Mars.mkv",
				"\\\\skywalker\\Media\\Other_Videos\\Dexter Videos - Baby\\Dexter - Fathers Day 2009.mp4"
		} ;
		
		for( String inputFileNamewithPath : inputFileNamesWithPaths )
		{
			final File inputFile = new File( inputFileNamewithPath ) ;
			
			log.info( "Testing " + inputFile.getAbsolutePath() ) ;
			
			final FileNamePattern fileNamePattern = new FileNamePattern( log, inputFile ) ;
			log.info( fileNamePattern.toString() ) ;
		}
	}
	
	/**
	 * Find patterns in the given file name.
	 * 	- Band Of Brothers - S01E01 - Curahee.mkv
	 *  - 2 Fast 2 Furious (2003).mkv
	 *  - Car Landing On Boat-behindthescenes.mkv
	 * @param inputFile
	 */
	protected void parsePattern()
	{
		// tvShowPattern and moviePattern apply only to the file name.
		final Pattern tvShowPattern = Pattern.compile( "(?<showName>.*) \\- S(?<seasonNumber>[\\d]+)E(?<episodeNumber>[\\d]+) \\- (?<episodeName>.*)\\.(?<extension>.*)" ) ;
		final Pattern moviePattern = Pattern.compile( "(?<movieName>.*) \\([\\d]{4}\\)\\.(?<extension>.*)" ) ;
		
		// movieExtraPattern and otherVideosPattern must be used on the entire path with file name.
		final Pattern movieExtraPattern = Pattern.compile( ".*\\\\(?<movieName>.*) \\([\\d]{4}\\)\\\\(?<extraName>.*)-(?<extraType>.*)\\.(?<extension>.*)" ) ;
		final Pattern otherVideosPattern = Pattern.compile( ".*Other_Videos\\\\(?<directoryName>.*)\\\\(?<fileName>.*)\\.(?<extension>.*)" ) ;
		
		final Matcher tvShowMatcher = tvShowPattern.matcher( inputFile.getName() ) ;
		if( tvShowMatcher.matches() )
		{
			// inputFile is a tv show.
			setTVShow( true ) ;
			setShowName( tvShowMatcher.group( "showName" ) ) ;
			setSeasonNumber( Integer.parseInt( tvShowMatcher.group( "seasonNumber" ) ) ) ;
			setEpisodeNumber( Integer.parseInt( tvShowMatcher.group( "episodeNumber" ) ) ) ;
			setEpisodeName( tvShowMatcher.group( "episodeName" ) ) ;
			setExtension( tvShowMatcher.group( "extension" ) ) ;
			
			return ;
		}
		
		final Matcher movieMatcher = moviePattern.matcher( inputFile.getName() ) ;
		if( movieMatcher.matches() )
		{
			setMovie( true ) ;
			setMovieName( movieMatcher.group( "movieName" ) ) ;
			setExtension( movieMatcher.group( "extension" ) ) ;

			return ;
		}
		
		final Matcher movieExtraMatcher = movieExtraPattern.matcher( inputFile.getAbsolutePath() ) ;
		if( movieExtraMatcher.matches() )
		{
			setMovieExtra( true ) ;
			setMovieName( movieExtraMatcher.group( "movieName" ) ) ;
			setExtraName( movieExtraMatcher.group( "extraName" ) ) ;
			setExtraType( movieExtraMatcher.group( "extraType" ) ) ;
			setExtension( movieExtraMatcher.group( "extension" ) ) ;
			
			return ;
		}
		
		final Matcher otherVideosMatcher = otherVideosPattern.matcher( inputFile.getAbsolutePath() ) ;
		if( otherVideosMatcher.matches() )
		{
			setOtherVideo( true ) ;
			setDirectoryName( otherVideosMatcher.group( "directoryName" ) ) ;
			setOtherVideoName( otherVideosMatcher.group( "fileName" ) ) ;
			setExtension( otherVideosMatcher.group( "extension" ) ) ;

			return ;
		}
		
		log.warning( "Unable to find pattern match for inputFile " + inputFile.getAbsolutePath() ) ;		
	}
	
	public String getDirectoryName()
	{
		return directoryName ;
	}

	public String getEpisodeName()
	{
		return episodeName ;
	}

	public int getEpisodeNumber()
	{
		return episodeNumber ;
	}

	public String getExtension()
	{
		return extension ;
	}

	public String getExtraName()
	{
		return extraName ;
	}

	public String getExtraType()
	{
		return extraType ;
	}

	/**
	 * Return the imdb show identifier extracted from the file path, or null if not found (not a tv show).
	 * @param theFile
	 * @return
	 */
	public static String getIMDBShowID( final String fileNameWithPath )
	{
		assert( fileNameWithPath != null ) ;
		return getIMDBShowID( new File( fileNameWithPath ) ) ;
	}
	
	/**
	 * Return the imdb show identifier extracted from the file path, or null if not found (not a tv show).
	 * @param theFile
	 * @return
	 */
	public static String getIMDBShowID( final File theFile )
	{
		assert( theFile != null ) ;

		final Pattern pathPattern = Pattern.compile( ".*\\{imdb-(?<imdbShowID>[\\d]+)\\}.*" ) ;
		final Matcher pathMatcher = pathPattern.matcher( theFile.getAbsolutePath() ) ;
		if( !pathMatcher.find() )
		{
			return null ;
		}
		// PC: Got a valid string for IMDB id
		final String imdbShowID = pathMatcher.group( "imdbShowID" ) ;
		return imdbShowID ;
	}
	
	public static String getTVDBShowID( final File theFile )
	{
		assert( theFile != null ) ;

		final Pattern pathPattern = Pattern.compile( ".*\\{tvdb-(?<tvdbShowID>[\\d]+)\\}.*" ) ;
		final Matcher pathMatcher = pathPattern.matcher( theFile.getAbsolutePath() ) ;
		if( !pathMatcher.find() )
		{
			return null ;
		}
		// PC: Got a valid string for TVDB id
		final String imdbShowID = pathMatcher.group( "tvdbShowID" ) ;
		return imdbShowID ;
	}
	
	/**
	 * Returns the season number from the given file path, or -1 if no season number found.
	 * @param theFile
	 * @return
	 */
	public static int getShowSeasonNumber( final File theFile )
	{
		assert( theFile != null ) ;
		
		int seasonNumber = -1 ;
		final Pattern seasonNumberPattern = Pattern.compile( ".*Season (?<seasonNumber>[\\d]+).*" ) ;
		final Matcher seasonNumberMatcher = seasonNumberPattern.matcher( theFile.getAbsolutePath() ) ;
		if( seasonNumberMatcher.find() )
		{
			final String seasonNumberString = seasonNumberMatcher.group( "seasonNumber" ) ;
			final Integer seasonNumberInteger = Integer.valueOf( seasonNumberString ) ;
			seasonNumber = seasonNumberInteger.intValue() ;
		}
		return seasonNumber ;
	}
	
	public String getMovieName()
	{
		return movieName ;
	}

	public String getOtherVideoName()
	{
		return otherVideoName ;
	}

	public int getSeasonNumber()
	{
		return seasonNumber ;
	}

	public String getShowName()
	{
		return showName ;
	}
	
	/**
	 * Convenience method to return a meaningful name as a prefix for this tv show/movie/other video. The requirement being filled here
	 * to provide a name that will establish enough uniqueness that a file related to this media file can be created in a shared folder.
	 * For example, if this file is an extra for 2 Fast 2 Furious, provide the name "2 Fast 2 Furious" that can be used as a prefix
	 * for any file related to it so that names can be created like "2 Fast 2 Furious_Making Of-behindthescenes.mkv"
	 * This will be used for placing this file into a directory where other similarly named files must reside (for example, "Making Of-behindthescenes"
	 *  is a common name).
	 * @return
	 */
	public String getShowOrMovieOrDirectoryName()
	{
		// Must be done in the order of most to least specific
		String name = "UNK" ;
		
		// Check for movie extra is first since it sets the isMovie flag to true
		if( isMovieExtra() )
		{
			name = getMovieName() ;
		}
		else if( isMovie() )
		{
			name = getMovieName() ;
		}
		else if( isTVShow() )
		{
			name = getShowName() ;
		}
		else if( isOtherVideo() )
		{
			name = getDirectoryName() ;
		}
		else
		{
			log.warning( "No name found: " + inputFile.getAbsolutePath() ) ;
		}
		return name ;
	}
	
	/**
	 * Convenience method that will retrieve a title for the given file, regardless of which type of media it represents.
	 * @return
	 */
	public String getTitle()
	{
		String theTitle = "" ;
		
		// Check performed in order of most to least specific.
		if( isMovieExtra() )
		{
			theTitle = getExtraName() ;
		}
		else if( isMovie() )
		{
			theTitle = getMovieName() ;
		}
		else if( isTVShow() )
		{
			theTitle = getEpisodeName() ;
		}
		else if( isOtherVideo() )
		{
			theTitle = getOtherVideoName() ;
		}
		else
		{
			log.warning( "Unable to find title for file " + inputFile.getAbsolutePath() ) ;
		}
		return theTitle ;
	}

	public boolean isTVShow()
	{
		return isTVShow ;
	}

	public boolean isMovie()
	{
		return isMovie ;
	}

	public boolean isMovieExtra()
	{
		return isMovieExtra ;
	}

	public boolean isOtherVideo()
	{
		return isOtherVideo ;
	}

	public void setDirectoryName( final String directoryName )
	{
		this.directoryName = directoryName ;
	}

	public void setEpisodeName( final String episodeName )
	{
		this.episodeName = episodeName ;
	}

	public void setEpisodeNumber( final int episodeNumber )
	{
		this.episodeNumber = episodeNumber ;
	}

	public void setExtension( final String extension )
	{
		this.extension = extension ;
	}

	public void setExtraName( final String extraName )
	{
		this.extraName = extraName ;
	}

	public void setExtraType( final String extraType )
	{
		this.extraType = extraType ;
	}

	public void setMovie( final boolean isMovie )
	{
		this.isMovie = isMovie ;
	}

	public void setMovieExtra( final boolean isMovieExtra )
	{
		this.isMovieExtra = isMovieExtra ;
	}

	public void setMovieName( final String movieName )
	{
		this.movieName = movieName ;
	}

	public void setOtherVideo( final boolean isOtherVideo )
	{
		this.isOtherVideo = isOtherVideo ;
	}

	public void setOtherVideoName( final String otherVideoName )
	{
		this.otherVideoName = otherVideoName ;
	}

	public void setSeasonNumber( final int seasonNumber )
	{
		this.seasonNumber = seasonNumber ;
	}

	public void setShowName( final String showName )
	{
		this.showName = showName ;
	}

	public void setTVShow( final boolean isTVShow )
	{
		this.isTVShow = isTVShow ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}	
}
