package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.WordUtils;

import com.google.gson.Gson;

/**
 * Renames TV shows to look like: "Show Name - SXXEYY - Episode Title.ext"
 * Uses a pattern matcher to identify extract the elements of the poorly formed name.
 */
public class FixTVShowNaming
{
	public TheTVDB tvDB = null ;

	/// Setup the logging subsystem
	protected transient Logger log = null ;
	protected transient Common common = null ;

	/// File name to which to log activities for this application.
	private static final String logFileName = "log_fix_tv_show_naming.txt" ;

	protected final String[] allowableExtensions = {
			"mp4",
			"mkv",
			"srt"
	} ;

	protected final String[] tvDirectories = {
			//			"\\\\skywalker\\usbshare1-2\\TV",
			//			"\\\\skywalker\\Media\\Staging\\Test"
//			"\\\\skywalker\\Media\\Staging\\TV_Shows",
			"\\\\skywalker\\Media\\Staging\\Archer (2009)"
			//			"\\\\skywalker\\Media\\TV_Shows\\House Of The Dragon (2022)\\Season 01"
	} ;
	protected final String[] extensions = {
			"mkv",
			"mp4"
	} ;

	// Pattern matching
	public class TV_Show_Pattern
	{
		/// Top level match to determine if the file is named incorrectly
		public String badPatternMatch = "" ;

		/// Patterns to extract show name, season and episode number, and episode name
		public String inputShowName = "" ;
		public String inputSeasonAndEpisodeNumber = "" ;
		public String inputEpisodeName = "" ;
		public String inputExtension = "" ;

		/// Output show name, season and episode number, and episode name
		public String outputShowName = "" ;
		public String outputSeasonAndEpisodeNumber = "" ;
		public String outputEpisodeName = "" ;
		public String outputExtension = "" ;

		public TV_Show_Pattern( final String badPatternMatch,
				final String inputShowName,
				final String inputSeasonAndEpisodeNumber,
				final String inputEpisodeName,
				final String inputExtension )
		{
			this.badPatternMatch = badPatternMatch ;
			this.inputShowName = inputShowName ;
			this.inputSeasonAndEpisodeNumber = inputSeasonAndEpisodeNumber ;
			this.inputEpisodeName = inputEpisodeName ;
			this.inputExtension = inputExtension ;
		}

		public String toString()
		{
			Gson loginRequestGson = new Gson() ;
			final String loginRequestJson = loginRequestGson.toJson( this ) ;
			return loginRequestJson.toString() ;
		}
	}

	public static void main( String[] args )
	{
		(new FixTVShowNaming()).run() ;
	}

	public FixTVShowNaming()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		common = new Common( log ) ;
		tvDB = new TheTVDB() ;
	}

	public void run()
	{
		common.setTestMode( false ) ;

		TV_Show_Pattern[] tvShowPatterns =
			{
					// Pattern: bad pattern, show name, season and episode number, episode name, extension
					// House of the Dragon_S01E01_The Heirs of the Dragon.mp4
					new TV_Show_Pattern( ".*_S[\\d]+E[\\d]+_.*\\..*", // bad pattern
							"(.*)_S[\\d]+E[\\d]+_.*\\..*", // show name
							".*_(S[\\d]+E[\\d]+)_.*", // S&E
							".*_S[\\d]+E[\\d]+_(.*)\\..*", // episode name
							".*_S[\\d]+E[\\d]+_.*\\.(.*)" // extension
							),

					// Archer.2009.S01E01.Mole.Hunt
					new TV_Show_Pattern( ".*\\.[0-9]{4}\\.S[0-9]+E[0-9]+\\..*", // bad pattern
							"(.*)\\.[0-9]{4}\\.S[0-9]+E[0-9]+\\..*", // show name
							".*\\.[0-9]{4}\\.(S[\\d]+E[\\d]+)\\..*", // S&E
							".*\\.[0-9]{4}\\.S[0-9]+E[0-9]+\\.(.*)\\..*", // episode name
							".*\\.[0-9]{4}\\.S[0-9]+E[0-9]+\\..*\\.(.*)" // extension
							)
			} ;

		for( String tvDirectory : tvDirectories )
		{
			File tvDirectoryFile = new File( tvDirectory ) ;
			if( !tvDirectoryFile.exists() )
			{
				log.warning( "Unable to build tvDirectoryFile for tvDirectory: " + tvDirectory ) ;
				continue ;
			}

			if( !tvDirectoryFile.isDirectory() )
			{
				continue ;
			}
			// tvDirectoryFile is a valid directory

			// List all matching files.
			List< Path > pathList = common.findFiles( tvDirectoryFile.getAbsolutePath(), "^.*\\.(mkv|mp4)$" ) ;
			//			List< Path > pathList = common.findFiles( tvDirectoryFile.getAbsolutePath(), "^.*\\_.*(mkv|mp4|srt)$" ) ;
			for( Path inputFilePath : pathList )
			{
				log.fine( "inputFilePath: " + inputFilePath.toString() ) ;
				final String inputFileName = inputFilePath.getFileName().toString() ;

				for( TV_Show_Pattern tvShowPattern : tvShowPatterns )
				{
					Pattern inputFileNamePattern = Pattern.compile( tvShowPattern.badPatternMatch ) ;
					Matcher inputFileNameMatcher = inputFileNamePattern.matcher( inputFileName ) ;
					if( !inputFileNameMatcher.find() )
					{
						// No match
						log.fine( "No Match between " + inputFileName + " and pattern: " + tvShowPattern.badPatternMatch ) ;
						continue ;
					}
					log.fine( "Found match between inputFileName " + inputFileName + " and badPatternMatch " + tvShowPattern.badPatternMatch ) ;

					// Extract the elements of the file name
					Pattern showNamePattern = Pattern.compile( tvShowPattern.inputShowName ) ;
					Matcher showNameMatcher = showNamePattern.matcher( inputFileName ) ;
					if( !showNameMatcher.find() )
					{
						log.warning( "Unable to find show name in " + inputFileName ) ;
						continue ;
					}
					String showName = showNameMatcher.group( 1 ) ;
					showName = WordUtils.capitalize( stripInvalidSubStrings( showName ) ) ;

					Pattern seasonAndEpisodeNumberPattern = Pattern.compile( tvShowPattern.inputSeasonAndEpisodeNumber ) ;
					Matcher seasonAndEpisodeNumberMatcher = seasonAndEpisodeNumberPattern.matcher( inputFileName ) ;
					if( !seasonAndEpisodeNumberMatcher.find() )
					{
						log.warning( "Unable to find seasonAndEpisodeNumber in " + inputFileName ) ;
						continue ;
					}
					String seasonAndEpisodeNumber = seasonAndEpisodeNumberMatcher.group( 1 ) ;
					seasonAndEpisodeNumber = WordUtils.capitalize( seasonAndEpisodeNumber ) ;

					Pattern episodeNamePattern = Pattern.compile( tvShowPattern.inputEpisodeName ) ;
					Matcher episodeNameMatcher = episodeNamePattern.matcher( inputFileName ) ;
					if( !episodeNameMatcher.find() )
					{
						log.warning( "Unable to find episodeName in " + inputFileName ) ;
						continue ;
					}
					String episodeName = episodeNameMatcher.group( 1 ) ;
					episodeName = WordUtils.capitalize( stripInvalidSubStrings( episodeName ) ) ;

					Pattern extensionPattern = Pattern.compile( tvShowPattern.inputExtension ) ;
					Matcher extensionMatcher = extensionPattern.matcher( inputFileName ) ;
					if( !extensionMatcher.find() )
					{
						log.warning( "Unable to find extension in " + inputFileName ) ;
						continue ;
					}
					String extension = extensionMatcher.group( 1 ) ;
//					extension = WordUtils.capitalize( extension ) ;

					log.fine( "showName: " + showName + ", seasonAndEpisodeNumber: " + seasonAndEpisodeNumber + ", episodeName: " + episodeName ) ;
					final String newFileName = showName + " - " + seasonAndEpisodeNumber + " - " + episodeName + "." + extension ;
					final String newFileWithPath = common.addPathSeparatorIfNecessary( inputFilePath.getParent().toString() ) + newFileName ;
					final File newFile = new File( newFileWithPath ) ;

					log.info( "Moving " + inputFilePath.toString() + " to " + newFileWithPath ) ;
					if( !common.getTestMode() )
					{
						try
						{
							Files.move( inputFilePath, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING );
						}
						catch( Exception theException )
						{
							log.warning( "Unable to move file from " + inputFilePath.toString() + " to " + newFile.getAbsolutePath() + ": " + theException.toString() ) ;
							continue ;
						}
					} // getTestMode()
				} // for( tvShowPattern )
			} // for( inputFilePath )
		} // for( tvDirectory )
	}

	/**
	 * Return true if the given inputExtension is an allowable extension.
	 * Match is performed case-insensitive.
	 * @param inputExtension
	 * @return
	 */
	public boolean isAllowableExtension( final String inputExtension )
	{
		assert( inputExtension != null ) ;
		for( String allowableExtension : allowableExtensions )
		{
			if( allowableExtension.equalsIgnoreCase( inputExtension ) )
			{
				// inputExtension is an allowable extension.
				return true ;
			}
		}
		return false ;
	}

	public String stripInvalidSubStrings( final String inputFileName )
	{
		assert( inputFileName != null ) ;

		String retMe = inputFileName ;
		retMe = retMe.replace( ",", "" ) ;

		// Remove '+' (M+A+S+H)
		retMe = retMe.replace( "+", "" ) ;
		retMe = retMe.replace( "&", "" ) ;
		retMe = retMe.replace( "--", "" ) ;
		//			retMe = retMe.replace( "- ", " " ) ;
		retMe = retMe.replace( "  ", " " ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "!", "" ) ;
		retMe = retMe.replace( ".", " " ) ;
		retMe = retMe.replace( ",", "" ) ;
		retMe = retMe.replace( "\'", "" ) ;
		retMe = retMe.replace( "?", "" ) ;
		retMe = retMe.replace( "#", "" ) ;
		retMe = retMe.replace( ":", "" ) ;
		retMe = retMe.replace( "...", "" ) ;
		retMe = retMe.replace( "*", "" ) ;

		return retMe ;
	}
}
