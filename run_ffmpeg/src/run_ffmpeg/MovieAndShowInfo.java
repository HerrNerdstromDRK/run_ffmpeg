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
 *
 */
public class MovieAndShowInfo implements Comparable< MovieAndShowInfo >
{
	/// The name of the movie.
	/// Will likely include the year, as in Plex format.
	/// Example: Godzilla King of the Monsters (2019)
	public String name = null ;
	public boolean isMissingFile = false ;
	public List< CorrelatedFile > correlatedFilesList = null ;

	/// If this is a TV show, then seasonName will contain the name of the season ("Season 04")
	/// If a movie, then seasonName will be null.
	//	private String seasonName = null ;

	/// The source (.mkv) files for this movie and its subordinate extras.
	private transient List< FFmpegProbeResult > mkvFilesByProbeResult = new ArrayList< FFmpegProbeResult >() ; 

	/// Same, but for the mp4 files
	private transient List< FFmpegProbeResult > mp4FilesByProbeResult = new ArrayList< FFmpegProbeResult >() ;

	/// Store the files that have been correlated, indexed by filename
	private transient Map< String, CorrelatedFile > correlatedFiles = new HashMap< String, CorrelatedFile >() ;
	
	/// Setup the logging subsystem
	private transient Logger log = null ;
	
	/// Constructor for a movie
	public MovieAndShowInfo( final String name, Logger log )
	{
		this.name = name ;
		this.log = log ;
	}
	
	@Override
	public int compareTo( MovieAndShowInfo rhs )
	{
		return name.compareTo( rhs.name ) ;
	}
	
	public void makeReadyCorrelatedFilesList()
	{
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
	 * Add an MKV file to the list of mkv files. This will include the full path to the file.
	 * @param mkvFileName
	 */
	public void addMKVFile( FFmpegProbeResult mkvProbeResult )
	{
		// Use on the file name, without the path or extension
		String fileName = (new File( mkvProbeResult.getFilename() )).getName().replace( ".mkv", "" ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = correlatedFiles.get( fileName ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileName ) ;
			correlatedFiles.put( fileName,  correlatedFile ) ;
		}
		correlatedFile.addMKVFile( mkvProbeResult ) ;
		mkvFilesByProbeResult.add( mkvProbeResult ) ;
	}

	/**
	 * Add an MP4 file to the list of mp4 files. This will include the full path to the file.
	 * @param mp4FileName
	 */
	public void addMP4File( FFmpegProbeResult mp4ProbeResult )
	{
		// Use on the file name, without the path or extension
		String fileName = (new File( mp4ProbeResult.getFilename() )).getName().replace( ".mp4", "" ) ;

		// First, look for an existing correlated file
		CorrelatedFile correlatedFile = correlatedFiles.get( fileName ) ;
		if( null == correlatedFile )
		{
			// Not found, create it.
			correlatedFile = new CorrelatedFile( fileName ) ;
			correlatedFiles.put( fileName,  correlatedFile ) ;
		}
		correlatedFile.addMP4File( mp4ProbeResult ) ;
		mp4FilesByProbeResult.add( mp4ProbeResult ) ;
	}

	public String getName()
	{
		return name ;
	}

	public String toString()
	{
		String retMe = "{name: " + getName()
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
