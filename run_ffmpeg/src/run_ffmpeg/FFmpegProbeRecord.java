package run_ffmpeg;

import java.util.Map;

/**
 * Store the information about an ffprobe run.
 * @author Dan
 *
 */
public class FFmpegProbeRecord
{
	/// The name of the file that was probed
	private String filename = null ;
	
	/// The number of streams
	public int nb_streams = 0 ;
	
	/// The number of programs (?)
	public int nb_programs = 0 ;

	/// The format (?)
	public String format_name = null ;
	
	/// The long format name (?)
	public String format_long_name = null ;
	
	/// The time, in ms, of the start of the probe
	public double start_time = 0 ;

	/// Stop time of the ffprobe
	public double stop_time = 0 ;

	/// Size of the file in bytes
	public long size = 0 ;

	/// Bitrate
	public long bit_rate = 0 ;

	/// Probe score (?)
	public int probe_score = 0 ;

	/// The file tags
	public Map<String, String> tags = null ;
}
