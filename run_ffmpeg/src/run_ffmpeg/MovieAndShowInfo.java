package run_ffmpeg;

import java.io.File;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

import com.google.gson.Gson;

import run_ffmpeg.ffmpeg.FFmpeg_ProbeResult;

/**
 * Encapsulates information about a movie or tv show. This includes all supporting files, such as extras.
 * @author Dan
 */
public class MovieAndShowInfo implements Comparable< MovieAndShowInfo >
{
	public ObjectId _id = null ;

	/// The path to the movie or tv show in long form (File.getParent()).
	/// Will likely include the year, as in Plex format.
	/// Note that at least one path could be empty, so makeReadyCorrelatedFilesList()
	///  will ensure each is non-empty ("unk" or other)
	public String fileLongPath = null ;

	/// The short version of the longName.
	/// If this is a TV show, then seasonName will contain the name of the season ("Season 04")
	/// If a movie, then seasonName will be null.
	/// Example: Godzilla King of the Monsters (2019)
	public String movieOrShowName = null ;

	/// This variable is only populated if this is a tv show
	//	public String tvShowSeasonName = "" ;
	public boolean tvShow = false ;

	/// Setup the logging subsystem
	private transient Logger log = null ;
	
	private final static String logFileName = "log_movie_and_show_info.txt" ;

	/**
	 * Default constructor for serialization/deserialization.
	 */
	public MovieAndShowInfo()
	{
		log = Common.setupLogger( logFileName, this.getClass().getName() ) ;
		assert( log != null ) ;
	}

	/**
	 * Constructor for a MovieOrShowInfo. This is a convenience constructor that requires the
	 *  caller to follow the creation of this object by configuring the strict tv showname (if appropriate).
	 * @param movieOrShowName
	 * @param log
	 */
	public MovieAndShowInfo( final String movieOrShowName, Logger log )
	{
		assert( movieOrShowName != null ) ;
		assert( log != null ) ;

		setMovieOrShowName( movieOrShowName ) ;
		this.log = log ;
	}

	/**
	 * Create an instance of MovieAndShowInfo and initialize the instance-level variables.
	 * Do NOT add the probeResult to any of the file structures -- that must be done
	 *  separately via add<MKV/MP4>File().
	 * @param theProbeResult
	 * @param log
	 */
	public MovieAndShowInfo( FFmpeg_ProbeResult theProbeResult, Logger log )
	{
		assert( log != null ) ;

		File theFile = new File( theProbeResult.getFileNameWithPath() ) ;
		this.log = log ;
		initObject( theFile ) ;
	}

	/**
	 * Create an instance of MovieAndShowInfo and initialize the instance-level variables.
	 * @param theFile A File object to be used for name parsing.
	 * @param log
	 */
	public MovieAndShowInfo( final File theFile, Logger log )
	{
		assert( log != null ) ;
		assert( theFile != null ) ;

		this.log = log ;
		initObject( theFile ) ;
	}

	/**
	 * Return the show name if it is a tv show, otherwise just return the inputFileName.
	 * @param inputFileName
	 * @return
	 */
	public static String extractTVShowName( final String inputFileName )
	{
		assert( inputFileName != null ) ;

		if( !inputFileName.contains( "Season " ) )
		{
			// Not a TV Show Name
			return inputFileName ;
		}
		// Post-Condition: TV Show
		final File inputFile = new File( inputFileName ) ;
		return extractTVShowName( inputFile ) ;
	}

	public static String extractTVShowName( final File inputFile )
	{
		assert( inputFile != null ) ;

		if( !inputFile.getAbsolutePath().contains( "Season " ) )
		{
			// Not a TV Show
			return inputFile.getName() ;
		}
		final String theTVShowName = inputFile.getParentFile().getParentFile().getName() ;
		return theTVShowName ;
	}

	/**
	 * Initialize the object. Test.
	 * In the event this is a TV show, be sure to strip off the trailing "Season XX" from the end of the paths.
	 * @param theFile
	 */
	private void initObject( final File theFile )
	{
		// Look for a "Season " in the path. However, if "Season " is followed by a (year) then it is a movie.
		if( theFile.getParent().contains( "Season " ) && !theFile.getParent().contains( "Season (" ) )
		{
			// Store tvShowName as "The Expanse" or "Arrested Development (2008)"
			// Be sure to remove any "Season 01" etc.
			final String tvShowName = extractTVShowName( theFile ) ;
			//			final String tvShowSeasonName = theFile.getParentFile().getName() ;
			log.fine( "Found TV show: " + tvShowName + ", filename: " + theFile.getName() ) ;

			setTVShow( true ) ;
			//			setTVShowName( tvShowName ) ;
			//			setTVShowSeasonName( tvShowSeasonName ) ;
			setMovieOrShowName( tvShowName ) ;
		}
		else if( theFile.getParent().contains( "(" ) )
		{
			// The formal filename should be like this:
			// \\yoda\Backup\Movies\Transformers (2007)\Making Of-behindthescenes.mkv
			final String movieName = theFile.getParentFile().getName() ;
			setMovieOrShowName( movieName ) ;
			log.fine( "Found movie: " + movieName + ", filename: " + theFile.getName() ) ;
			// movieName should be of the form "Transformers (2007)"
		}
		else if( theFile.getAbsolutePath().contains( "Other Videos" ) )
		{
			// Do nothing for Other Videos
		}
		else
		{
			log.warning( "Parse error for file: " + theFile.getAbsolutePath() ) ;
		}
		
		if( theFile.getName().endsWith( ".mp4" ) )
		{
			if( isTVShow() )
			{
				// Strip the "Season XX"
//				setMP4LongPath( theFile.getParentFile().getParent() ) ;
			}
			else
			{
//				setMP4LongPath( theFile.getParent() ) ;
			}
		}
		else
		{
			if( isTVShow() )
			{
//				setMKVLongPath( theFile.getParentFile().getParent() ) ;
			}
			else
			{
//				setMKVLongPath( theFile.getParent() ) ;
			}
		}
	}

	/**
	 * Strip off any instance of "Season xx" from the given file path and return everything before the Season xx.
	 * @param inputFilePath
	 * @return
	 */
	public static String stripSeasonNameDirectory( final String inputFilePath )
	{
		if( !inputFilePath.contains( "Season " ) )
		{
			return inputFilePath ;
		}
		// Post condition: inputFileNameWithPath is a tvShow with a Season directory in the path.
		final File inputFile = new File( inputFilePath ) ;

		// Most likely, inputFileNameWithPath is of the form: "\\\\yoda\\MKV_ArchiveX\\TV Shows\\Show Name\\Season xx"
		// Need to strip out Season xx
		String returnFilePath = inputFile.getParent() ;
		while( returnFilePath.contains( "Season " ) )
		{
			File nextInputFile = new File( returnFilePath ) ;
			returnFilePath = nextInputFile.getParent() ;
		}
		return returnFilePath ;
	}

	@Override
	public int compareTo( MovieAndShowInfo rhs )
	{
		return movieOrShowName.compareTo( rhs.movieOrShowName ) ;
	}

//	/**
//	 * Return the largest file in this MovieAndShowInfo.
//	 * Returns null if nothing found, although this shouldn't happen.
//	 * @return
//	 */
	public FFmpeg_ProbeResult findLargestFile()
	{
		FFmpeg_ProbeResult largestFile = null ;
// TODO
		//		for( CorrelatedFile theCorrelatedFile : correlatedFilesList )
//		{
//			final FFmpegProbeResult localLargestFile = theCorrelatedFile.findLargestFile() ;
//
//			if( null == localLargestFile )
//			{
//				log.warning( "Error: Found a null file in CorrelatedFile: " + theCorrelatedFile.toString() ) ;
//				continue ;
//			}
//			// Post condition: localLargestFile != null ;
//			if( null == largestFile )
//			{
//				largestFile = localLargestFile ;
//			}
//			else if( localLargestFile.getSize() > largestFile.getSize() )
//			{
//				largestFile = localLargestFile ;
//			}
//		}
		return largestFile ;
	}

	public String getMovieOrShowName()
	{
		return movieOrShowName ;
	}

	public void setMovieOrShowName( final String movieOrShowName )
	{
		this.movieOrShowName = movieOrShowName ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}

	public boolean isTVShow()
	{
		return tvShow ;
	}

	public void setTVShow( boolean newTVShow )
	{
		tvShow = newTVShow ;
	}
}
