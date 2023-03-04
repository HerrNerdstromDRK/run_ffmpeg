package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Encapsulates information about a movie or tv show. This includes all supporting files, such as extras.
 * @author Dan
 */
public class MovieAndShowInfo implements Comparable< MovieAndShowInfo >
{
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
	
	/// These two variables are only populated if this is a tv show
	public String TVShowName = "" ;
	public String TVShowSeasonName = "" ;
	
	/// Set to true if this movie or show is missing at least one mkv or mp4 file
	public boolean isMissingFile = false ;

	/// List that contains matching information for each mkv or mp4 file.
	/// This is the structure that will be stored in the database.
	public List< CorrelatedFile > correlatedFilesList = null ;

	/// The source (.mkv) files for this movie and its subordinate extras.
	private transient List< FFmpegProbeResult > mkvFilesByProbeResult = new ArrayList< FFmpegProbeResult >() ; 

	/// Same, but for the mp4 files
	private transient List< FFmpegProbeResult > mp4FilesByProbeResult = new ArrayList< FFmpegProbeResult >() ;

	/// Store the files that have been correlated, indexed by filename
	private transient Map< String, CorrelatedFile > correlatedFiles = new HashMap< String, CorrelatedFile >() ;
	
	/// Setup the logging subsystem
//	private transient Logger log = null ;
	
	/// Constructor for a movie
	public MovieAndShowInfo( final String movieOrShowName, Logger log )
	{
		this.movieOrShowName = movieOrShowName ;
//		this.log = log ;
	}
	
	public MovieAndShowInfo()
	{}
	
	@Override
	public int compareTo( MovieAndShowInfo rhs )
	{
		return movieOrShowName.compareTo( rhs.movieOrShowName ) ;
	}
	
	/**
	 * Walk through the map of files associated with this Movie or TV Show and build
	 *  the list of CorrelatedFiles that can be stored into the database.
	 */
	public void makeReadyCorrelatedFilesList()
	{
		if( null == mkvLongPath ) mkvLongPath = "(empty)" ;
		if( null == mp4LongPath ) mp4LongPath = "(empty)" ;
		
		correlatedFilesList = new ArrayList< CorrelatedFile >() ;
		for( Map.Entry< String, CorrelatedFile > entrySet : correlatedFiles.entrySet() )
		{
			CorrelatedFile theCorrelatedFile = entrySet.getValue() ;
			if( theCorrelatedFile.isMissingFile() )
			{
				isMissingFile = true ;
			}
			theCorrelatedFile.normalizeMKVAndMP4Files() ;
			correlatedFilesList.add( theCorrelatedFile ) ;
		}
		Collections.sort( correlatedFilesList ) ;
	}

	/**
	 * Return the largest file in this MovieAndShowInfo. It could be an mkv or mp4.
	 * Returns null if nothing found, although this shouldn't happen.
	 * @return
	 */
	public FFmpegProbeResult findLargestFile()
	{
		FFmpegProbeResult largestFile = null ;
		for( FFmpegProbeResult testFile : mkvFilesByProbeResult )
		{
			if( null == largestFile )
			{
				largestFile = testFile ;
				continue ;
			}
			if( testFile.getSize() > largestFile.getSize() )
			{
				largestFile = testFile ;
			}
		}
		for( FFmpegProbeResult testFile : mp4FilesByProbeResult )
		{
			if( null == largestFile )
			{
				largestFile = testFile ;
				continue ;
			}
			if( testFile.getSize() > largestFile.getSize() )
			{
				largestFile = testFile ;
			}
		}
		return largestFile ;
	}
	
	/**
	 * Add an MKV file to the map and list of mkv files. This will include the full path to the file.
	 * @param mkvFileName
	 */
	public void addMKVFile( FFmpegProbeResult mkvProbeResult )
	{
		File probeResultFile = new File( mkvProbeResult.getFilename() ) ;
		final String probeResultParentPath = probeResultFile.getParent() ;
		if( null == mkvLongPath )
		{
			// empty mkvLongPath, update it here
			mkvLongPath = probeResultParentPath ;
		}
		// Use on the file name, without the path or extension
		String fileNameWithoutExtension = probeResultFile.getName().replace( ".mkv", "" ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = correlatedFiles.get( fileNameWithoutExtension ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileNameWithoutExtension ) ;
			correlatedFiles.put( fileNameWithoutExtension,  correlatedFile ) ;
		}
		correlatedFile.addMKVFile( mkvProbeResult ) ;
		mkvFilesByProbeResult.add( mkvProbeResult ) ;
	}

	/**
	 * Add an MP4 file to the map and list of mp4 files. This will include the full path to the file.
	 * @param mp4FileName
	 */
	public void addMP4File( FFmpegProbeResult mp4ProbeResult )
	{
		File probeResultFile = new File( mp4ProbeResult.getFilename() ) ;
		final String probeResultParentPath = probeResultFile.getParent() ;
		if( null == mp4LongPath )
		{
			// empty mkvLongPath, update it here
			mp4LongPath = probeResultParentPath ;
		}
		
		// Use on the file name, without the path or extension
		String fileNameWithoutExtension = probeResultFile.getName().replace( ".mp4", "" ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = correlatedFiles.get( fileNameWithoutExtension ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileNameWithoutExtension ) ;
			correlatedFiles.put( fileNameWithoutExtension,  correlatedFile ) ;
		}
		correlatedFile.addMP4File( mp4ProbeResult ) ;
		mp4FilesByProbeResult.add( mp4ProbeResult ) ;
	}

	public String getMovieOrShowName()
	{
		return movieOrShowName ;
	}

	public String getTVShowName() {
		return TVShowName;
	}

	public void setTVShowName(String tVShowName) {
		TVShowName = tVShowName;
	}

	public String getTVShowSeasonName() {
		return TVShowSeasonName;
	}

	public void setTVShowSeasonName(String tVShowSeasonName) {
		TVShowSeasonName = tVShowSeasonName;
	}

	public boolean isTVShow()
	{
		return !getTVShowSeasonName().isBlank() ;
	}

	public String toString()
	{
		String retMe = "{name: " + getMovieOrShowName()
		+ ",mkvLongPath:" + mkvLongPath
		+ ",mp4LongPath:" + mp4LongPath
		+ ",TVShowName:" + getTVShowName()
		+ ",TVShowSeasonName:" + getTVShowSeasonName()
		+ ",isMissingFile:" + isMissingFile
		+ ",mkv:[" ;
		for( FFmpegProbeResult mkvFileProbeResult : mkvFilesByProbeResult )
		{
			retMe += mkvFileProbeResult.getFilename() + " " ;
		}
		retMe += "],mp4:[" ;
		for( FFmpegProbeResult mp4FileProbeResult : mp4FilesByProbeResult )
		{
			retMe += mp4FileProbeResult.getFilename() + " " ;
		}
		retMe += "],correlateFiles:{" ;
		for( Map.Entry< String, CorrelatedFile > set : correlatedFiles.entrySet() )
		{
			final String key = set.getKey() ;
			final CorrelatedFile correlatedFile = set.getValue() ;
			retMe += "[" + key + "," + correlatedFile.toString() + "]," ;
		}
		retMe += "}" ;
		return retMe ;
	}
	
}
