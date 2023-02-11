package run_ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Encapsulates information about a movie or tv show. This includes all supporting files, such as extras.
 * @author Dan
 *
 */
public class MovieAndShowInfo
{
	/// Setup the logging subsystem
	private transient Logger log = null ;
	
	/// The name of the movie.
	/// Will likely include the year, as in Plex format.
	/// Example: Godzilla King of the Monsters (2019)
	private String name = null ;
	
	/// If this is a TV show, then seasonName will contain the name of the season ("Season 04")
	/// If a movie, then seasonName will be null.
//	private String seasonName = null ;
	
	/// The source (.mkv) files for this movie and its subordinate extras.
	private List< FFmpegProbeResult > mkvFilesByProbeResult = new ArrayList< FFmpegProbeResult >() ; 

	/// Same, but for the mp4 files
	private List< FFmpegProbeResult > mp4FilesByProbeResult = new ArrayList< FFmpegProbeResult >() ;
	
	/// Store the files that have been correlated, indexed by filename
	private Map< String, CorrelatedFile > correlatedFiles = new HashMap< String, CorrelatedFile >() ;
	
	/// Constructor for a movie
	public MovieAndShowInfo( final String name, Logger log )
	{
		this.name = name ;
		this.log = log ;
	}
	
	/**
	 * Walk through all mp4 and mkv entries to establish correlations between each.
	 */
	public void correlateProbeResults()
	{
		// Possible that one mkv file will correlate to two mp4 files by mistake.
		// The opposite is also possible.
		// Therefore, establish a structure that permits multiple correlations for each file.
		// This will be used to identify errors.
		// TODO: Move this to addMKVFile/addMP4File

		// Start by walking through the mkv files.

		// Post condition: All mkv probe results added to correlatedFile map
		
		// Do the same for the mp4 files

		// Post condition: The correlated files structure has all mk4 and mp4 files.
		
		// Now look for missing or duplicated entries.
		for( Map.Entry< String, CorrelatedFile > set : correlatedFiles.entrySet() )
		{
			final String fileName = set.getKey() ;
			final CorrelatedFile correlatedFile = set.getValue() ;
			
			if( correlatedFile.mkvFiles.isEmpty() )
			{
				log.warning( "Empty mkvFiles for entry: " + fileName ) ;
			}
			else if( correlatedFile.mkvFiles.size() > 1 )
			{
				log.warning( "More than one mkv file for entry: " + correlatedFile.mkvFiles.toString() ) ;
			}
			else
			{
//				log.info( "Good MKV correlation for entry: " + fileName ) ;
			}
			
			if( correlatedFile.mp4Files.isEmpty() )
			{
				log.warning( "Empty mkvFiles for entry: " + fileName ) ;
			}
			else if( correlatedFile.mp4Files.size() > 1 )
			{
				log.warning( "More than one mp4 file for entry: " + fileName ) ;
			}
			else
			{
//				log.info( "correlateProbeResults> Good MP4 correlation for entry: " + fileName ) ;
			}
		}	
	}
	
	/**
	 * Add an MKV file to the list of mkv files. This will include the full path to the file.
	 * @param mkvFileName
	 */
	public void addMKVFile( FFmpegProbeResult mkvProbeResult )
	{
//		for( FFmpegProbeResult mkvFile : mkvFilesByProbeResult )
//		{
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
			correlatedFile.mkvFiles.add( mkvProbeResult ) ;
//		}
//		mkvFilesByProbeResult.add( mkvProbeResult ) ;
	}

	/**
	 * Add an MP4 file to the list of mp4 files. This will include the full path to the file.
	 * @param mp4FileName
	 */
	public void addMP4File( FFmpegProbeResult mp4ProbeResult )
	{
//		for( FFmpegProbeResult mp4File : mp4FilesByProbeResult )
//		{
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
			correlatedFile.mp4Files.add( mp4ProbeResult ) ;
//		}
//		mp4FilesByProbeResult.add( mp4ProbeResult ) ;
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
		retMe += "]}" ;
		return retMe ;
	}
	
}
