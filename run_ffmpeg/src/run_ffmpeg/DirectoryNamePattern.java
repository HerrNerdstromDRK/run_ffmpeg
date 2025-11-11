package run_ffmpeg;

import java.io.File;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DirectoryNamePattern
{
	protected transient File inputDirectory = null ;
	protected transient Logger log = null ;
	protected static final transient String logFileName = "log_directorynamepattern.txt" ;

	protected boolean isTVShow = false ;
	protected boolean isMovie = false ;
	protected boolean hasEditionInfo = false ;
	protected boolean hasImdbInfo = false ;
	protected boolean hasTmdbInfo = false ;
	protected boolean hasTvdbInfo = false ;
	protected String editionInfo = "" ;

	protected String imdbInfo = "" ;
	protected String tmdbInfo = "" ;
	protected String tvdbInfo = "" ;
	protected String showOrMovieName = "" ;
	protected String year = "" ;

	/**
	 * inputDirectory must be the full path to the input directory for a movie or tv show. For a tv show, it must be to the
	 *  top level directory of the show, (not a Season directory).
	 * @param log
	 * @param inputDirectory
	 */
	public DirectoryNamePattern( Logger log, File inputDirectory )
	{
		Preconditions.checkArgument( log != null ) ;
		Preconditions.checkArgument( inputDirectory != null ) ;
		Preconditions.checkArgument( inputDirectory.exists() ) ;
		Preconditions.checkArgument( inputDirectory.isDirectory() ) ;

		this.log = log ;
		this.inputDirectory = inputDirectory ;

		parseInputDirectory() ;
	}

	private void parseInputDirectory()
	{
		// inputDirectory is the full path to the movie or tv show top folder.
		// The last segment of the path may have different optional arguments
		// \\skywalker\\Media\\Movies\\2 Fast 2 Furious (2003)
		// 2 Fast 2 Furious (2003) {edition-4K}
		// A Beautiful Day In The Neighborhood (2019) {imdb-3224458}
		// An Uncommon Grace (2017) {imdb-6173488} {tvdb-28960}
		// Big Mommas Like Father Like Son (2011) {edition-Extended} {imdb-1464174}
		// The Lord Of The Rings The Return Of The King {edition-4K Extended} (2003)
		// 24 (2001) {imdb-0285331} {tvdb-76290}

		// Each tv show is expected to have at least one subdirectory, so I will use that, albeit
		// inconclusive, metric to determine if I'm dealing with a tv show or movie.

		// First, determine if this is a tv show or movie.
		if( hasSubDirectories( getInputDirectory() ) )
		{
			setTVShow( true ) ;
		}
		else
		{
			setMovie( true ) ;
		}

		// baseName will hold the full string of movie/tv show with all of the extras (imdb/tvdb/edition/etc.)
		final String baseName = getInputDirectory().getName().trim() ;

		/// I struggled for a while to create a singled regex pattern that would everything I need.
		/// In the end, I must admit defeat in favor of something much simpler that just works.
		/// I tried placing these at object scope but apparently I cannot reuse Patterns
		final Pattern nameAndYearPattern = Pattern.compile( "(?<name>[^()]+)\\s+\\((?<year>[\\d]{4})\\)\\s*" ) ;
		final Pattern editionInfoPattern = Pattern.compile( "(\\{edition-(?<editionInfo>[a-zA-Z0-9 ]+)\\})" ) ;
		final Pattern imdbInfoPattern = Pattern.compile( "(\\{imdb-(?<imdbInfo>(\\d+))\\})" ) ;
		final Pattern tmdbInfoPattern = Pattern.compile( "(\\{tmdb-(?<tmdbInfo>(\\d+))\\})" ) ;
		final Pattern tvdbInfoPattern = Pattern.compile( "(\\{tvdb-(?<tvdbInfo>(\\d+))\\})" ) ;
		
		// (?<name>[^()]+)\s+\((?<year>[\d]{4})\)\s*(?:(?:(\{tmdb-(?<tmdbInfo>[\d]+)\})\s*)?(?:(\{edition-(?<editionInfo>[a-zA-Z0-9 ]+)\})\s*)?(?:(\{imdb-(?<imdbInfo>[\d]+)\})\s*)?(?:(\{tvdb-(?<tvdbInfo>[\d]+)\})\s*)?)?
//		final Pattern thePattern = Pattern.compile( "(?<name>[^()]+)\\s*\\((?<year>[\\d]{4})\\)\\s*((\\{edition-(?<editionInfo>[a-zA-Z0-9 ]+)\\})?\\s*(\\{imdb-(?<imdbInfo>\\d+)\\})?\\s*(\\{tmdb-(?<tmdbInfo>\\d+)\\})?\\s*(\\{tvdb-(?<tvdbInfo>\\d+)\\})?)?" ) ;
		final Matcher nameAndYearMatcher = nameAndYearPattern.matcher( baseName ) ;
		if( !nameAndYearMatcher.find() )
		{
			// This is a mandatory group so this is an error
			log.warning( "Unable to find name/year in baseName: " + baseName ) ;
			return ;
		}
		// PC: Name and year present
		setShowOrMovieName( nameAndYearMatcher.group( "name" ).trim() ) ;
		setYear( nameAndYearMatcher.group( "year" ) ) ;
		
		final Matcher editionInfoMatcher = editionInfoPattern.matcher( baseName ) ;
		if( editionInfoMatcher.find() )
		{
			setEditionInfo( editionInfoMatcher.group( "editionInfo" ) ) ;
			setHasEditionInfo( true ) ;
		}
		final Matcher imdbInfoMatcher = imdbInfoPattern.matcher( baseName ) ;
		if( imdbInfoMatcher.find() )
		{
			setImdbInfo( imdbInfoMatcher.group( "imdbInfo" ) ) ;
			setHasImdbInfo( true ) ;
		}
		final Matcher tmdbInfoMatcher = tmdbInfoPattern.matcher( baseName ) ;
		if( tmdbInfoMatcher.find() )
		{
			setTmdbInfo( tmdbInfoMatcher.group( "tmdbInfo" ) ) ;
			setHasTmdbInfo( true ) ;
		}
		final Matcher tvdbInfoMatcher = tvdbInfoPattern.matcher( baseName ) ;
		if( tvdbInfoMatcher.find() )
		{
			setTmdbInfo( tvdbInfoMatcher.group( "tvdbInfo" ) ) ;
			setHasTvdbInfo( true ) ;
		}
	}

	public static void main( final String[] args )
	{
		Logger log = Common.setupLogger( logFileName, "DirectoryNamePattern" ) ;

		final File testDir = new File( "\\\\skywalker\\Media\\Test" ) ;
		Set< File > testDirectories = Stream.of( testDir.listFiles() )
				.filter( file -> file.isDirectory() )
				.collect( Collectors.toSet() ) ;
		log.info( "Will check these directories: " + testDirectories.toString() ) ;

		for( File inputDirFile : testDirectories )
		{
			log.info( "inputDirFile: " + inputDirFile.getAbsolutePath() ) ;
			DirectoryNamePattern dnp = new DirectoryNamePattern( log, inputDirFile ) ;
			log.info( dnp.toString() ) ;
		}
	}

	public String getAbsolutePath()
	{
		return inputDirectory.getAbsolutePath() ;
	}

	public String getDirectoryName()
	{
		String dirName = getShowOrMovieName()
				+ " (" + getYear() + ")"
				+ (hasImdbInfo() ? (" {imdb-" + getImdbInfo() + "}") : "")
				+ (hasTmdbInfo() ? (" {tmdb-" + getTmdbInfo() + "}") : "") 
				+ (hasTvdbInfo() ? (" {tvdb-" + getTvdbInfo() + "}") : "")
				+ (hasEditionInfo() ? (" {edition-" + getEditionInfo() + "}") : "") ;
		return dirName ;
	}

	public String getEditionInfo()
	{
		return editionInfo;
	}

	public String getImdbInfo()
	{
		return imdbInfo;
	}

	public File getInputDirectory()
	{
		return inputDirectory ;
	}

	public static String getLogfilename()
	{
		return logFileName;
	}

	public String getShowOrMovieName()
	{
		return showOrMovieName;
	}

	public String getTmdbInfo()
	{
		return tmdbInfo;
	}

	public String getTvdbInfo()
	{
		return tvdbInfo;
	}

	public String getYear()
	{
		return year ;
	}

	public boolean hasEditionInfo()
	{
		return hasEditionInfo;
	}

	public boolean hasImdbInfo()
	{
		return hasImdbInfo;
	}

	public boolean hasSubDirectories( final File testDirectory )
	{
		Preconditions.checkArgument( testDirectory != null ) ;

		return testDirectory.isDirectory() && (testDirectory.listFiles( File::isDirectory ).length > 0) ;
	}

	public boolean hasTmdbInfo()
	{
		return hasTmdbInfo;
	}

	public boolean hasTvdbInfo()
	{
		return hasTvdbInfo;
	}

	public boolean isMovie()
	{
		return isMovie ;
	}

	public boolean isTVShow()
	{
		return isTVShow ;
	}

	public void setEditionInfo( final String editionInfo )
	{
		Preconditions.checkArgument( editionInfo != null ) ;

		this.editionInfo = editionInfo;
	}

	public void setHasEditionInfo( boolean hasEditionInfo )
	{
		this.hasEditionInfo = hasEditionInfo;
	}

	public void setHasImdbInfo( boolean hasImdbInfo )
	{
		this.hasImdbInfo = hasImdbInfo;
	}

	public void setHasTmdbInfo( boolean hasTmdbInfo )
	{
		this.hasTmdbInfo = hasTmdbInfo;
	}

	public void setHasTvdbInfo( boolean hasTvdbInfo )
	{
		this.hasTvdbInfo = hasTvdbInfo;
	}

	public void setMovie( final boolean isMovie )
	{
		this.isMovie = isMovie ;
	}

	public void setShowOrMovieName( final String showOrMovieName )
	{
		Preconditions.checkArgument( showOrMovieName != null ) ;

		this.showOrMovieName = showOrMovieName;
	}

	public void setImdbInfo( final String imdbInfo )
	{
		Preconditions.checkArgument( imdbInfo != null ) ;

		this.imdbInfo = imdbInfo;
	}

	public void setTmdbInfo( final String tmdbInfo )
	{
		Preconditions.checkArgument( tmdbInfo != null ) ;

		this.tmdbInfo = tmdbInfo;
	}

	public void setTvdbInfo( final String tvdbInfo )
	{
		Preconditions.checkArgument( tvdbInfo != null ) ;

		this.tvdbInfo = tvdbInfo;
	}

	public void setTVShow( final boolean isTVShow )
	{
		this.isTVShow = isTVShow ;
	}

	public void setYear( final String year )
	{
		Preconditions.checkArgument( year != null ) ;

		this.year = year ;
	}

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ; 
		Gson gson = builder.create() ;
		String json = gson.toJson( this ) ;
		return json ;
	}
}
