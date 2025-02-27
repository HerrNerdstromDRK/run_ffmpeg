package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.bson.types.ObjectId;

/**
 * Encapsulates information about a movie or tv show. This includes all supporting files, such as extras.
 * @author Dan
 */
public class MovieAndShowInfo implements Comparable< MovieAndShowInfo >
{
	public ObjectId _id = null ;

	/// The path to the movie or tv show in long form (File.getParent()).
	/// Will likely include the year, as in Plex format.
	/// Store the path for both mkv and mp4.
	/// These variables will be updated in add{MKV,MP4}File() methods.
	/// Note that at least one path could be empty, so makeReadyCorrelatedFilesList()
	///  will ensure each is non-empty ("unk" or other)
	public String mkvLongPath = null ;
	public String mp4LongPath = null ;

	/// The short version of the longName.
	/// If this is a TV show, then seasonName will contain the name of the season ("Season 04")
	/// If a movie, then seasonName will be null.
	/// Example: Godzilla King of the Monsters (2019)
	public String movieOrShowName = null ;

	/// This variable is only populated if this is a tv show
	//	public String tvShowSeasonName = "" ;
	public boolean tvShow = false ;

	/// Set to true if this movie or show is missing at least one mkv or mp4 file
	public boolean isMissingFile = false ;

	/// List that contains matching information for each mkv or mp4 file.
	/// This is the structure that will be stored in the database.
	public List< CorrelatedFile > correlatedFilesList = new ArrayList< CorrelatedFile >() ;

	/// Store the files that have been correlated, indexed by filename
	//	private transient Map< String, CorrelatedFile > correlatedFiles = new HashMap< String, CorrelatedFile >() ;

	/// Setup the logging subsystem
	private transient Logger log = null ;

	/**
	 * Default constructor for serialization/deserialization.
	 */
	public MovieAndShowInfo()
	{
		log = Common.setupLogger( "log_movie_and_show_info.txt", this.getClass().getName() ) ;
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
	public MovieAndShowInfo( FFmpegProbeResult theProbeResult, Logger log )
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
				setMP4LongPath( theFile.getParentFile().getParent() ) ;
			}
			else
			{
				setMP4LongPath( theFile.getParent() ) ;
			}
		}
		else
		{
			if( isTVShow() )
			{
				setMKVLongPath( theFile.getParentFile().getParent() ) ;
			}
			else
			{
				setMKVLongPath( theFile.getParent() ) ;
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

	/**
	 * Add an MKV file to the map and list of mkv files. This will include the full path to the file.
	 * @param mkvFileName
	 */
	public void addMKVFile( FFmpegProbeResult mkvProbeResult )
	{
		final File probeResultFile = new File( mkvProbeResult.getFileNameWithPath() ) ;
		final String probeResultParentPath = probeResultFile.getParent() ;
		if( null == mkvLongPath )
		{
			// empty mkvLongPath, update it here
			mkvLongPath = stripSeasonNameDirectory( probeResultParentPath ) ;
		}
		// Use on the file name, without the path or extension
		// Example: "Making Of-behindthescenes.mkv" -> "Making Of-behindthescenes"
		String fileNameWithoutExtension = Common.removeFileNameExtension( probeResultFile.getName() ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = findCorrelatedFileByNameWithoutExtension( fileNameWithoutExtension ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileNameWithoutExtension ) ;
			correlatedFilesList.add( correlatedFile ) ;
		}
		correlatedFile.addOrUpdateMKVFile( mkvProbeResult ) ;
	}

	/**
	 * Add an MP4 file to the map and list of mp4 files. This will include the full path to the file.
	 * @param mp4FileName
	 */
	public void addMP4File( FFmpegProbeResult mp4ProbeResult )
	{
		final File probeResultFile = new File( mp4ProbeResult.getFileNameWithPath() ) ;
		final String probeResultParentPath = probeResultFile.getParent() ;
		if( null == mp4LongPath )
		{
			// empty mkvLongPath, update it here
			mp4LongPath = stripSeasonNameDirectory( probeResultParentPath ) ;
		}

		// Use on the file name, without the path or extension
		final String fileNameWithoutExtension = probeResultFile.getName().replace( ".mp4", "" ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = findCorrelatedFileByNameWithoutExtension( fileNameWithoutExtension ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileNameWithoutExtension ) ;
			correlatedFilesList.add( correlatedFile ) ;
		}
		correlatedFile.addOrUpdateMP4File( mp4ProbeResult ) ;
	}

	@Override
	public int compareTo( MovieAndShowInfo rhs )
	{
		return movieOrShowName.compareTo( rhs.movieOrShowName ) ;
	}

	/**
	 * Retrieve the CorrelatedFile from the list of files. If not found, return null.
	 * @param fileNameWithoutExtension
	 * @return
	 */
	public CorrelatedFile findCorrelatedFileByNameWithoutExtension( final String fileNameWithoutExtension )
	{
		CorrelatedFile retMe = null ;
		for( CorrelatedFile testFile : correlatedFilesList )
		{
			if( testFile.getFileName().equals( fileNameWithoutExtension ) )
			{
				retMe = testFile ;
				break ;
			}
		}
		return retMe ;
	}

	/**
	 * Return the largest file in this MovieAndShowInfo. It could be an mkv or mp4.
	 * Returns null if nothing found, although this shouldn't happen.
	 * @return
	 */
	public FFmpegProbeResult findLargestFile()
	{
		FFmpegProbeResult largestFile = null ;
		for( CorrelatedFile theCorrelatedFile : correlatedFilesList )
		{
			final FFmpegProbeResult localLargestFile = theCorrelatedFile.findLargestFile() ;

			if( null == localLargestFile )
			{
				log.warning( "Error: Found a null file in CorrelatedFile: " + theCorrelatedFile.toString() ) ;
				continue ;
			}
			// Post condition: localLargestFile != null ;
			if( null == largestFile )
			{
				largestFile = localLargestFile ;
			}
			else if( localLargestFile.getSize() > largestFile.getSize() )
			{
				largestFile = localLargestFile ;
			}
		}
		return largestFile ;
	}

	/**
	 * Not using an iterator here because it was breaking the serialization. *shrug*
	 * @return
	 */
	public List< CorrelatedFile > getCorrelatedFilesList()
	{
		return correlatedFilesList ;
	}

	public String getMKVLongPath()
	{
		return mkvLongPath;
	}

	public String getMovieOrShowName()
	{
		return movieOrShowName ;
	}

	public String getMP4LongPath()
	{
		return mp4LongPath;
	}

	//	public String getTVShowName()
	//	{
	//		return tvShowName;
	//	}

	//	public String getTVShowSeasonName()
	//	{
	//		return tvShowSeasonName;
	//	}
	//
	//	public boolean isTVShow()
	//	{
	//		return !getTVShowSeasonName().isBlank() ;
	//	}

	/**
	 * Walk through the map of files associated with this Movie or TV Show and build
	 *  the list of CorrelatedFiles that can be stored into the database.
	 */
	public void makeReadyCorrelatedFilesList()
	{
		isMissingFile = false ;

		if( null == mkvLongPath ) mkvLongPath = Common.getMissingFileSubstituteName() ;
		if( null == mp4LongPath ) mp4LongPath = Common.getMissingFileSubstituteName() ;

		for( CorrelatedFile theCorrelatedFile : correlatedFilesList )
		{
			// Order these two items is important:
			// normalize() checks for missing files.
			theCorrelatedFile.normalizeMKVAndMP4Files() ;
			if( theCorrelatedFile.isMissingFile() )
			{
				isMissingFile = true ;
			}
		}
		Collections.sort( correlatedFilesList ) ;
	}

	public void setMKVLongPath( String mkvLongPath )
	{
		this.mkvLongPath = mkvLongPath;
	}

	public void setMovieOrShowName( final String movieOrShowName )
	{
		this.movieOrShowName = movieOrShowName ;
	}

	public void setMP4LongPath(String mp4LongPath) 
	{
		this.mp4LongPath = mp4LongPath;
	}

	//	public void setTVShowName( String tvShowName )
	//	{
	//		this.tvShowName = tvShowName;
	//	}

	//	public void setTVShowSeasonName( String tvShowSeasonName )
	//	{
	//		this.tvShowSeasonName = tvShowSeasonName;
	//	}

	public String toString()
	{
		String retMe = "{movieOrShowName: " + getMovieOrShowName()
		+ ",mkvLongPath:" + getMKVLongPath()
		+ ",mp4LongPath:" + getMP4LongPath()
		//		+ ",TVShowName:" + getTVShowName()
		//		+ ",TVShowSeasonName:" + getTVShowSeasonName()
		+ ",tvShow: " + isTVShow()
		+ ",isMissingFile:" + isMissingFile + ",correlatedFilesList:{" ;
		for( CorrelatedFile correlatedFileIterator : correlatedFilesList )
		{
			retMe += correlatedFileIterator.toString() + "," ;
		}
		retMe += "}}" ;
		return retMe ;
	}

	/**
	 * Update a correlated file, probably because the file has had a probe update.
	 * @param theFile
	 */
	public void updateCorrelatedFile( final FFmpegProbeResult theProbeResult )
	{
		// First, find the CorrelatedFile in the list
		// Note that correlated file names are stored without extension
		File theProbeResultFile = new File( theProbeResult.getFileNameWithPath() ) ;
		String fileNameToSearch = Common.removeFileNameExtension( theProbeResultFile.getName() ) ;
		CorrelatedFile theCorrelatedFile = null ;

		for( CorrelatedFile correlatedFileIterator : correlatedFilesList )
		{
			if( correlatedFileIterator.getFileName().equals( fileNameToSearch ) )
			{
				// Found it.
				theCorrelatedFile = correlatedFileIterator ;
				break ;
			}
		}
		if( null == theCorrelatedFile )
		{
			log.warning( "Unable to find correlated file; MovieAndShowInfo name: " + getMovieOrShowName()
			+ ", theFile: " + theProbeResultFile.getAbsolutePath() ) ;
			return ;
		}
		// Post condition: theCorrelatedFile is non-null and represents the file being searched.

		if( theProbeResultFile.getName().contains( ".mkv" ) )
		{
			// MKV file
			theCorrelatedFile.addOrUpdateMKVFile( theProbeResult ) ;
		}
		else
		{
			// MP4 file
			theCorrelatedFile.addOrUpdateMP4File( theProbeResult ) ;
		}
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
