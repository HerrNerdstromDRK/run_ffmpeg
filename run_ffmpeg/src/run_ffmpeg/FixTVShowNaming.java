package run_ffmpeg;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.WordUtils;

import com.google.gson.Gson;

import run_ffmpeg.TheTVDB.TheTVDB_ShowInfo;
import run_ffmpeg.TheTVDB.TheTVDB;

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
	
	/// Store all tv shows that are drawn from the TVDB to reduce the number of external calls made here.
	protected Map< Integer, TheTVDB_ShowInfo > tvDBShows = new HashMap< Integer, TheTVDB_ShowInfo >() ;
	
	/// The pattern that matches a correctly formed TV show file name
	protected static final String patternCorrectTVShowName = ".* - S[\\d]+E[\\d]+ - .*\\.(mkv|mp4)" ;
	protected static final String patternCorrectTVSeasonDirectoryName = "Season [\\d]+" ;

	protected final String[] allowableExtensions =
	{
			"mp4",
			"mkv",
			"srt"
	} ;

	protected final List< String > tvDirectories = new ArrayList< String >() ;
	
//	{
//			//			"\\\\skywalker\\usbshare1-2\\TV",
//			"\\\\skywalker\\Media\\Staging\\TV_Shows"
//			//			"\\\\skywalker\\Media\\Staging\\Archer (2009)"
//			//			"\\\\skywalker\\Media\\TV_Shows\\House Of The Dragon (2022)\\Season 01"
//	} ;
	
	protected final String[] extensions =
	{
			"mkv",
			"mp4"
	} ;

	// Pattern matching
	public class TV_Show_Pattern
	{
		/// The name of this pattern, mostly to keep straight for debugging
		public String patternName = "UNNAMED" ;
		
		/// Top level match to determine if the file is named incorrectly
		public String badPatternMatch = "" ;

		/// Patterns to extract show name, season and episode number, and episode name
		public String namedCaptureGroupsString = "" ;

		public boolean missingEpisodeName = false ;

		public static final String showNameString = "showName" ;
		public static final String seasonAndEpisodeString = "seasonAndEpisode" ;
		public static final String episodeNameString = "episodeName" ;
		public static final String extensionString = "extension" ;

		public TV_Show_Pattern( final String patternName,
				final String badPatternMatch,
				final String namedCaptureGroupsString,
				boolean missingEpisodeName )
		{
			this.patternName = patternName ;
			this.badPatternMatch = badPatternMatch ;
			this.namedCaptureGroupsString = namedCaptureGroupsString ;
			this.missingEpisodeName = missingEpisodeName ;
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
		common.setTestMode( true ) ;
		
		tvDirectories.add( Common.getPathToToOCR() ) ;
//		tvDirectories.add( Common.getPathToTVShows() ) ;
//		tvDirectories.add( common.addPathSeparatorIfNecessary( Common.getPathToTVShows() ) + "Brooklyn Nine-Nine (2013) {tvdb-269586}" ) ; 
		
		reportMalformedTVShowNames() ;
//		fixMalformedTVShowNames() ;
	}

	public void reportMalformedTVShowNames()
	{
		int numMalformedTVShowNames = 0 ;
		int numMalformedTVSeasonNames = 0 ;
		log.info( "Starting..." ) ;
		
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
			// PC: tvDirectoryFile is a valid directory
			
			// List all matching files.
			final List< Path > pathList = common.findFiles( tvDirectoryFile.getAbsolutePath(), "^.*\\.(mkv|mp4)$" ) ;
			final Pattern correctTVShowNamePattern = Pattern.compile( patternCorrectTVShowName ) ;
			final Pattern correctTVSeasonNamePattern = Pattern.compile( patternCorrectTVSeasonDirectoryName ) ;
			
			for( Path inputFilePath : pathList )
			{
				final File inputFile = inputFilePath.toFile() ;
				final String inputFileName = inputFile.getName() ;
				Matcher fileMatcher = correctTVShowNamePattern.matcher( inputFileName ) ;
				
				if( !fileMatcher.matches() )
				{
					++numMalformedTVShowNames ;
					log.info( "Found improperly formed inputFileName: " + inputFileName ) ;
				}
				
				// Check if the parent directory is anything but "Season XX"
				final String parentDirectoryString = inputFile.getParentFile().getName() ;
				Matcher tvSeasonNameMatcher = correctTVSeasonNamePattern.matcher( parentDirectoryString ) ;
				
				if( !tvSeasonNameMatcher.matches() )
				{
					++numMalformedTVSeasonNames ;
					log.info( "Found malformed season directory for file " + inputFile.getAbsolutePath() ) ;
				}				
			} // for( inputFilePath )
		} // for( tvDirectory )
		log.info( "Done. Found " + numMalformedTVShowNames + " malformed tv show name(s) and " + numMalformedTVSeasonNames + " malformed season name(s)" ) ;
	}
	
	public void fixMalformedTVShowNames()
	{
		final TV_Show_Pattern[] tvShowPatterns =
			{
					// Pattern: bad pattern, show name, season and episode number, episode name, extension
					// House of the Dragon_S01E01_The Heirs of the Dragon.mp4
					new TV_Show_Pattern( "House of the Dragon",
							".*_S[\\d]+E[\\d]+_.*\\..*", // bad pattern
							"(?<showName>.*)_(?<seasonAndEpisode>S[\\d]+E[\\d]+)_(?<episodeName>.*)\\.(?<extension>.*)",
							false ),

					// Archer.2009.S01E01.Mole.Hunt.mp4
					new TV_Show_Pattern( "Archer",
							".*\\.[\\d]{4}\\.S[\\d]+E[\\d]+\\..*", // bad pattern
							"(?<showName>.*)\\.[\\d]{4}\\.(?<seasonAndEpisode>(S[\\d]+E[\\d]+)\\.(?<episodeName>).*)\\.(?<extension>.*)",
							false ),

					// The Acolyte (2024) - S01E02 - Revenge Justice.mp4
					new TV_Show_Pattern( "Acolyte",
							".* \\([0-9]{4}\\) \\- S[0-9]+E[0-9]+ \\- .*\\..*", // bad pattern
							"(?<showName>.*) \\([0-9]{4}\\) \\- (?<seasonAndEpisode>S[0-9]+E[0-9]+) \\- (?<episodeName>.*)\\.(?<extension>.*)",
							false ),

					// The Office (U.S.).S01E01.mp4
					// Important this pattern is before the next pattern
					new TV_Show_Pattern( "The Office",
							"^.* \\(U\\.S\\.\\)\\.S[0-9]+E[0-9]+\\.(mkv|mp4)$",
							"(?<showName>.*) \\(U\\.S\\.\\)\\.(?<seasonAndEpisode>S[0-9]+E[0-9]+)\\.(?<extension>.*)",
							true ),

					// Modern Family.S01E01.mp4
					new TV_Show_Pattern( "Modern Family",
							".*\\.S[0-9]+E[0-9]+\\..*",
							"(?<showName>.*)\\.(?<seasonAndEpisode>S[0-9]+E[0-9]+)\\.(?<extension>.*)",
							true ),					

					// Star Wars_ The Bad Batch_S02E08_Truth and Consequences.mp4
					new TV_Show_Pattern( "The Bad Batch",
							".*_ .*_S[0-9]+E[0-9]+_.*\\..*",
							"(?<showName>.*)_(?<seasonAndEpisode>S[0-9]+E[0-9]+)_(?<episodeName>.*)\\.(?<extension>.*)",
							true )					

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
			
			// For each file (Path), look for matches against the known bad patterns.
			for( Path inputFilePath : pathList )
			{
//				log.fine( "inputFilePath: " + inputFilePath.toString() ) ;
				final String inputFileName = inputFilePath.getFileName().toString() ;
				
				// Record if we find a pattern match to ensure we only change the name of the file once.
				// Note that this imposes a requirement that the more specific patterns be listed first.
				boolean foundMatch = false ;

				// Check this file against each bad pattern
				for( TV_Show_Pattern tvShowPattern : tvShowPatterns )
				{
					if( foundMatch )
					{
						// Already found a match, no more matching please.
						break ;
					}
					
					Pattern inputFileNamePattern = Pattern.compile( tvShowPattern.badPatternMatch ) ;
					Matcher inputFileNameMatcher = inputFileNamePattern.matcher( inputFileName ) ;
					if( !inputFileNameMatcher.find() )
					{
						// No match
//						log.fine( "No Match between " + inputFileName + " and pattern: " + tvShowPattern.badPatternMatch ) ;
						continue ;
					}
					foundMatch = true ;

					log.info( "Found match between inputFileName " + inputFileName + " and pattern " + tvShowPattern.patternName
							+ " (" + tvShowPattern.badPatternMatch + ")" ) ;
					
					// Extract the elements of the file name
					Pattern namedCaptureGroupPattern = Pattern.compile( tvShowPattern.namedCaptureGroupsString ) ;
					Matcher namedCaptureGroupMatcher = namedCaptureGroupPattern.matcher( inputFileName ) ;

					if( !namedCaptureGroupMatcher.matches() )
					{
						// Found a match to the pattern, but not the grouping.
						// Log the issue but allow a match against the next bad pattern.
						foundMatch = false ;
						
						log.fine( "Non-matching capture group for file: " + inputFileName + " (" + tvShowPattern.patternName + ")" ) ;
						continue ;
					}

					String showName = namedCaptureGroupMatcher.group( TV_Show_Pattern.showNameString ) ;
					showName = WordUtils.capitalize( stripInvalidSubStrings( showName ) ) ;

					String seasonAndEpisodeNumber = namedCaptureGroupMatcher.group( TV_Show_Pattern.seasonAndEpisodeString ) ;
					seasonAndEpisodeNumber = WordUtils.capitalize( seasonAndEpisodeNumber ) ;

					String episodeName = "" ;
					if( tvShowPattern.missingEpisodeName )
					{
						episodeName = getEpisodeName( inputFilePath, showName, seasonAndEpisodeNumber ) ;
					}
					else
					{
						episodeName = namedCaptureGroupMatcher.group( TV_Show_Pattern.episodeNameString ) ;
					}
					
					if( (null == episodeName) || episodeName.isBlank() )
					{
						log.warning( "Unable to get episodeName for: " + inputFileName ) ;
						continue ;
					}
					episodeName = WordUtils.capitalize( stripInvalidSubStrings( episodeName ) ) ;

					String extension = namedCaptureGroupMatcher.group( TV_Show_Pattern.extensionString ) ;
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
	 * Return the episode name, or empty string if not found.
	 * @param tvShowPattern
	 * @param seasonAndEpisodeNumber
	 * @return
	 */
	public String getEpisodeName( final Path inputFilePath, final String showName, final String seasonAndEpisodeNumberString )
	{
		// Extract the TVDB ID
		if( !inputFilePath.toString().contains( "{tvdb-" ) )
		{
			log.warning( "Missing TVDB information for file " + inputFilePath.toString() ) ;
			return "" ;
		}
		// Post-condition: The path has the tvdb information included

		Pattern tvdbIdPattern = Pattern.compile( ".*\\{tvdb\\-(?<tvdbID>[\\d]+)\\}.*S(?<seasonNumber>[\\d]+)E(?<episodeNumber>[\\d]+).*" ) ;
		Matcher tvdbIdMatcher = tvdbIdPattern.matcher( inputFilePath.toString() ) ;
		if( !tvdbIdMatcher.matches() )
		{
			log.warning( "Unable to match to ID in file " + inputFilePath.toString() ) ;
			return "" ;
		}
		
		final String showIdString = tvdbIdMatcher.group( "tvdbID" ) ;
		final String seasonNumberString = tvdbIdMatcher.group( "seasonNumber" ) ;
		final String episodeNumberString = tvdbIdMatcher.group( "episodeNumber" ) ;
		
		final Integer showIdInteger = Integer.parseInt( showIdString ) ;
		final Integer seasonNumberInteger = Integer.parseInt( seasonNumberString ) ;
		final Integer episodeNumberInteger = Integer.parseInt( episodeNumberString ) ;
		log.fine( "Got showId " + showIdInteger.toString() + ", seasonNumber "
				+ seasonNumberInteger
				+ ", episodeNumber "
				+ episodeNumberInteger
				+ " from file " + inputFilePath.toString() ) ;
		
		// Check if this show has already been found
		TheTVDB_ShowInfo theShowInfo = tvDBShows.get( showIdInteger ) ;
		if( null == theShowInfo )
		{
			TheTVDB tvdb = new TheTVDB( log, common ) ;
			theShowInfo = tvdb.getFullShowInfo( showIdInteger ) ;
			if( null == theShowInfo )
			{
				// Failed to lookup show
				log.warning( "Failed to lookup show " + showIdString ) ;
				return "" ;
			}
			// Post-condition: successful lookup for the show
			
			// Record the show info for a future call
			tvDBShows.put( showIdInteger, theShowInfo ) ;
		}
		return theShowInfo.getEpisodeName( seasonNumberInteger.intValue(), episodeNumberInteger.intValue() ) ;
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
		retMe = retMe.replace( ":", "" ) ;
		retMe = retMe.replace( "_", "" ) ;
		retMe = retMe.replace( "*", "" ) ;

		return retMe ;
	}
}
