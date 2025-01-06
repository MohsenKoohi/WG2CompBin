// We convert an arc labelled graph in Web Graph format to binary files
// The first binary file contains offsets with 8 bytes per vertex in little endian order and for |V|+1 vertices
// The second binary file is the edges file with 4 bytes for neighbour and 4 bytes for label per edge

// https://search.maven.org/search?q=it.unimi.dsi

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.NoSuchElementException;
import it.unimi.dsi.big.webgraph.*;
import java.util.concurrent.atomic.*;
import java.text.SimpleDateFormat ;
import java.util.Date;

public class WG2Bin
{	
	private static int threads_count = 0;
	private static String input_path;
	private static String output_folder;
	private static String output_props_file;
	private static String output_offsets_file;
	private static String output_edges_file;
	private static String filename;	
	private static long threads_total_edges[];
	private SimpleDateFormat df;
	private int vertex_ID_bytes = 4;

	static public void main(String[] args)
	{	
		// Initial checks
			System.out.println("\n\033[1;32mWebGraph 2 Binary Convertor\033[0;37m");
			if(args.length != 2)
			{
				System.out.println("Args: path/to/graph path/to/bin/folder\n\n");
				return;
			}

			input_path = args[0];
			output_folder = args[1];

			filename = input_path.replace(new File(input_path+".graph").getParent()+"/","");
			output_offsets_file = output_folder + "/" + filename + "_offsets.bin";
			output_edges_file = output_folder + "/" + filename + "_edges.bin";
			output_props_file = output_folder + "/" + filename + "_props.txt";

			System.out.println("input_path: " + input_path);
			System.out.println("output_folder: " + output_folder);
			System.out.println("output_offsets_file: " +  "\033[1;34m" + output_offsets_file +  "\033[0;37m" );
			System.out.println("output_edges_file:   " +  "\033[1;34m" + output_edges_file +  "\033[0;37m" );

		// Starting
			new WG2Bin();

		System.out.println();

		return;
	}

	private WG2Bin()
	{
		df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

		threads_count = Runtime.getRuntime().availableProcessors();
		System.out.println("threads_count: " + threads_count);

		try
		{
			// Reading the input graph
				long t0 = -System.nanoTime();
				ImmutableGraph graph = ImmutableGraph.loadMapped(input_path);
				System.out.format("Graph Init. Time: %,.2f seconds\n",(t0 + System.nanoTime())/1e9);
				System.out.println("RandomAccess: " + graph.randomAccess());
				System.out.format("#Nodes: %,d\n", graph.numNodes());
				System.out.format("#Arcs: %,d\n", graph.numArcs());

				if(!graph.randomAccess())
				{
					System.out.println("The graph is not a random access graph.");
					return;
				}

				vertex_ID_bytes = (int)Math.ceil(Math.log(graph.numNodes())/Math.log(2)/8);
				System.out.println("vertex_ID_bytes:" + vertex_ID_bytes);

			// Writing the props file
			{
				FileWriter fw = new FileWriter(output_props_file);
				PrintWriter pw = new PrintWriter(fw);
				pw.println("vertices-count:" + graph.numNodes());
				pw.println("edges-count:" + graph.numArcs());
				pw.println("bytes-per-vertex-ID-in-edges-file:" + vertex_ID_bytes);
				pw.println("offsets-file:" + filename + "_offsets.bin");
				pw.println("edges-file:" + filename + "_edges.bin");
				pw.flush();
				pw.close();
				fw.close();
		  }

			WriterThread.Initialize();
			WriterThread wt[] = new WriterThread[threads_count];
			threads_total_edges = new long[threads_count];

			// Step 0: Each thread calculate the sum of degrees in its partition and then the prefix sum is computed
			// Step 1: Each thread writes the offsets to output_offsets_file and also stores threads_jobs_offsets
			// Step 2: Threads write edges into output_edges_file
			
			for(int step = 0; step < 3; step++)
			{
				System.out.println(getTime() + "Step " + step + ": Started.");

				// Creating the output files
					if(step == 1)
					{
						long f_size = 8L * (graph.numNodes() + 1);
						RandomAccessFile f = new RandomAccessFile(output_offsets_file, "rw");
						f.setLength(f_size);
						f.close();

						System.out.println("The offsets.bin file created on " + output_offsets_file+", size: "+ f_size);
					}
					if(step == 2)
					{
						long f_size = vertex_ID_bytes * graph.numArcs();

						RandomAccessFile f = new RandomAccessFile(output_edges_file, "rw");
						f.setLength(f_size);
						f.close();

						System.out.println("The edges.bin file created on " + output_edges_file +", size: "+ f_size);
					}

				for(int t=0; t<threads_count; t++)
				{
					wt[t] = new WriterThread(graph, t, step);
					wt[t].start();
				}

				for(int t=0; t<threads_count; t++)
					wt[t].join();

				System.out.println(getTime() + "Step " + step + ": Done.\n");

				// Prefix sum of threads_total_edges to identify their offsets
				if(step == 0)
				{
					long sum = 0;
					for(int t=0; t<threads_count; t++)
					{
						long temp = threads_total_edges[t];
						threads_total_edges[t] = sum;
						sum += temp;
					}

					if(sum != graph.numArcs())
					{
						System.out.println("Error in calcuating offsets: "+sum+" != "+graph.numArcs());
						return;
					}
				}

				// Check if all partitions have been processed
				if(step == 2)
				{
					for(int t = 0; t < threads_count; t++)
						for(int job = 1; job < WriterThread.threads_jobs[t][0] - 1; job++)
							assert WriterThread.threads_jobs_done[t][job].get() == true;


					System.out.format("Step-2: Written edges: %,d\n", WriterThread.written_edges.get());
					assert WriterThread.written_edges.get() == graph.numArcs();
					System.out.format("Step-2: Write bandwidth:  %,.2f MB/s \n", 1e3 * WriterThread.written_edges.get() * vertex_ID_bytes / (t0 + System.nanoTime()));
				}
			}

			System.out.format("\nTotal Exec. Time: %,.2f seconds\n",(t0 + System.nanoTime())/1e9);
		}
		catch(Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		return;
	}

	class WriterThread extends Thread
	{
		static private AtomicBoolean threads_jobs_done [][];
		static private long threads_jobs [][];
		static private long threads_jobs_offsets [][];
		static private AtomicLong completed_partitions;
		static private AtomicLong written_edges;
		static private AtomicLong progress;
		static private AtomicInteger progress_next;

		private ImmutableGraph graph;
		private int thread_id;
		private int step;
		private ByteBuffer intByteBuffer;
		private ByteBuffer longByteBuffer;
	
		class Partition
		{
			public long start_vertex,end_vertex,start_offset, end_offset;

			public Partition(long sv, long ev, long so, long eo)
			{
				start_vertex = sv;
				end_vertex = ev;
				start_offset = so;
				end_offset = eo;
			}
		}

		public static void Initialize()
		{
			threads_jobs_done = new AtomicBoolean[threads_count][];
			threads_jobs = new long[threads_count][];
			threads_jobs_offsets = new long[threads_count][];

			completed_partitions = new AtomicLong(0L);
			written_edges = new AtomicLong(0L);

			progress = new AtomicLong(0L);
			progress_next = new AtomicInteger(1);

			return;
		}

		public WriterThread(ImmutableGraph graph, int thread_id, int step)
		{
			this.graph = graph.copy();
			this.thread_id = thread_id;
			this.step = step;

			longByteBuffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
	
			return;
		}

		private Partition getNextPartition()
		{
			for(int tr = 0; tr < threads_count; tr++)
			{
				int t = thread_id + tr;
				if(t >= threads_count)
					t -= threads_count;

				for(int job = 1; job < threads_jobs[t][0] - 1; job++)
				{
					if(threads_jobs_done[t][job].get())
						continue;

					if(threads_jobs_done[t][job].compareAndSet(false, true))
					{
						long sv = threads_jobs[t][job];
						long ev = threads_jobs[t][job + 1];
						long so = threads_jobs_offsets[t][job];
						long eo = threads_jobs_offsets[t][job + 1];
						return new Partition(sv, ev, so, eo);
					}
					else
						continue;
				}
			}

			return null;
		}

		public void run()
		{
			long vertices_count = graph.numNodes();
			long edges_count = graph.numArcs();

			long start_vertex = thread_id * (vertices_count / threads_count);
			long end_vertex = (1 + thread_id) * (vertices_count / threads_count);
			if(thread_id == threads_count -1)
				end_vertex = vertices_count;

			long max_buffer_size;
			if(edges_count < 10L * 1024 * 1024 * 1024) 
				max_buffer_size = 32L * 1024 * 1024;
			else
				max_buffer_size = 128L * 1024 * 1024;
			if(thread_id == 0 && step == 0)
				System.out.println("\tEdges buffer length: " + (max_buffer_size/(1024L* 1024)) + " Million per thread");

			int paritions_count =(int)( edges_count / max_buffer_size + 1);

			try
			{
				if(step == 0)
				{
					threads_jobs[thread_id] = new long[paritions_count + 2];

					long sum = 0;
					int tjc = 1;
					threads_jobs[thread_id][tjc++] = start_vertex;
					long tjc_sum = 0;
					
					int pv = 0;
					for(long v = start_vertex; v < end_vertex; v++)
					{
						long degree = graph.outdegree(v);
						sum += degree;

						tjc_sum += degree;
						if(tjc_sum > max_buffer_size || v == end_vertex - 1)
						{
							threads_jobs[thread_id][tjc++] = v + 1;
							tjc_sum = 0;
						}

						if(pv++ == 1000)
						{
							pv = 0;
							long pvs = progress.addAndGet(1000);
							int n = progress_next.get();
							if(100.0 * pvs / graph.numNodes() >= n)
								if(progress_next.incrementAndGet() == n + 1)
									System.out.println("\t"+getTime()+"Progress: \033[1;32m" + n + "%\033[0;37m.");
						}
					}

					assert tjc <= paritions_count + 2;

					threads_jobs[thread_id][0] = tjc;
					threads_total_edges[thread_id] = sum;

					threads_jobs_done[thread_id] = new AtomicBoolean[tjc];
					for(int j = 1; j < tjc - 1; j++)
						threads_jobs_done[thread_id][j] = new AtomicBoolean(false);
					threads_jobs_offsets[thread_id] = new long[tjc];
				}
				else if(step == 1)
				{
					RandomAccessFile randomAccessFile = new RandomAccessFile(output_offsets_file, "rw");
					long start_offset = 8 * (start_vertex);
					long total_bytes = 8 * (end_vertex - start_vertex);
					if(thread_id == threads_count - 1)
						total_bytes += 8;

					long buffer_size_limit = 1024 * 1024 * 128;

					long total_written_bytes = 0;
					long round_written_bytes = 0;
					long round_limit = Math.min(total_bytes - total_written_bytes, buffer_size_limit);
					MappedByteBuffer buffer = randomAccessFile.getChannel().map(
						FileChannel.MapMode.READ_WRITE, start_offset, round_limit
					);

					int tjc = 1;
					long sum = threads_total_edges[thread_id];
					
					for(long v = start_vertex; v < end_vertex; v++)
					{
						buffer.put(longByteBuffer.rewind().putLong(sum).array());

						if(v == threads_jobs[thread_id][tjc])
							threads_jobs_offsets[thread_id][tjc++] = sum;

						long degree = graph.outdegree(v);
						sum += degree;

						round_written_bytes += 8;
						assert round_written_bytes <= round_limit;

						if(round_written_bytes == round_limit)
						{
							buffer.force();
							
							start_offset += round_limit;
							total_written_bytes += round_limit;
							
							round_written_bytes = 0;
							round_limit = Math.min(total_bytes - total_written_bytes, buffer_size_limit);
							buffer = randomAccessFile.getChannel().map(
								FileChannel.MapMode.READ_WRITE, start_offset, round_limit
							);
						}
					}
					
					threads_jobs_offsets[thread_id][tjc++] = sum;
					if(tjc  != threads_jobs[thread_id][0])
					{
						System.out.println("Error, tjc does not match");
						return;
					}
					if(thread_id == threads_count - 1)
					{
						assert sum == graph.numArcs();
						buffer.put(longByteBuffer.rewind().putLong(sum).array());
					}

					buffer.force();
					randomAccessFile.close();
				}
				else if(step == 2)
				{
					RandomAccessFile randomAccessFile = new RandomAccessFile(output_edges_file, "rw");
					
					int total_paritions_count = 0;
					for(int t=0; t<threads_count; t++)
						total_paritions_count += threads_jobs[t][0] - 2;

					long tte = 0;

					while(true)
					{		
						Partition p = getNextPartition();
						if(p == null)
							break;

						// if(thread_id == 0)
							// System.out.println("Stolen: "+p.start_vertex+" "+thread_id);

						start_vertex = p.start_vertex;
						end_vertex = p.end_vertex;

						long start_byte = vertex_ID_bytes * p.start_offset;
						long length = vertex_ID_bytes * (p.end_offset - p.start_offset);
						if(length == 0)
							continue;

						MappedByteBuffer buffer = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE, start_byte, length);
						
						for(long v = start_vertex; v < end_vertex; v++)
						{
							long degree = graph.outdegree(v);
							LazyLongIterator it = graph.successors(v);

							long n = 0;
							while(n < degree)
							{
								long dest = it.nextLong();
								assert dest != -1;

								buffer.put(longByteBuffer.rewind().putLong(dest).array(), 0, vertex_ID_bytes);

								n++;
							}

							tte += degree;
						}

						buffer.force();

						long cp = completed_partitions.getAndIncrement();
						if(cp % (threads_count/2) == 0)
							System.out.println("\t"+getTime()+"Progress: \033[1;32m"+ cp +"\033[0;37m / "+ total_paritions_count +" Completed.");

					}
					randomAccessFile.close();

					written_edges.getAndAdd(tte);
				}

			}
			catch(Exception e)
			{
				System.out.println(e.getMessage());
				e.printStackTrace();
			}

			return;
		}

	}

	private String getTime()
	{
		return "\033[0;32m" + df.format(new java.util.Date())+ "\033[0;37m ";
	}
}
