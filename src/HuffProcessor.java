import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = getCounts(in);
		HuffNode tree = makeTree(counts);
		String[] encodings = makeEncodings(tree);

		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(tree, out);
		in.reset();
		writeCompressedBits(encodings, in, out);

		out.close();
	}

	/**
	 * Helper method for determining the frequencies of every character
	 * in the file.
	 * @param in Buffered bit stream of the file to be compressed
	 * @return int[] where int[i] is the frequency of (char)i
	 */
	private int[] getCounts(BitInputStream in)
	{
		int[] counts = new int[257];

		while (true)
		{
			int bit = in.readBits(BITS_PER_WORD);
			if (bit == -1) break;
			counts[bit]++;
		}

		counts[PSEUDO_EOF] = 1;

		return counts;
	}


	/**
	 * Helper method for generating an encoding tree for the file to be compressed.
	 * @param counts array of frequencies for every character in file.
	 * @return root node of encoding tree.
	 */
	private HuffNode makeTree(int[] counts)
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();


		for(int i = 0; i < counts.length; i++) {
			if (counts[i] > 0)
				pq.add(new HuffNode(i,counts[i],null,null));
		}

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}

		HuffNode root = pq.remove();

		return root;

	}


	/**
	 * Helper method for generating encodings using the encoding tree.
	 * @param root Root of the encoding tree.
	 * @return String[] where String[i] is the encoding of (char)i in String form.
	 */
	private String[] makeEncodings(HuffNode root)
	{
		String[] encodings = new String[ALPH_SIZE + 1];
		recurse(root,"",encodings);
		return encodings;
	}

	private void recurse(HuffNode root, String path, String[] encodings)
	{
		if (root.myLeft == null && root.myRight == null)
		{
			encodings[root.myValue] = path;
			return;
		}

		recurse(root.myLeft, path+"0", encodings);
		recurse(root.myRight, path+"1", encodings);
	}

	/**
	 * Helper method for writing the encoding tree to the output file in bits.
	 * @param root Root of the encoding tree.
	 * @param out  Buffered bit stream writing to the output file.
	 */
	private void writeTree(HuffNode root, BitOutputStream out)
	{
		HuffNode node = root;


		if (node.myLeft == null && node.myRight == null)
		{
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, node.myValue);
			return;
		}

		out.writeBits(1, 0);
		writeTree(root.myLeft, out);
		writeTree(root.myRight, out);
	}

	/**
	 * Helper method for compressing the data from the input file using the encodings
	 * given and writing to output file.
	 * @param encodings String[] where String[i] is the encoding for (char)i
	 * @param in Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	private void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out)
	{
		while (true)
		{
			int bit = in.readBits(BITS_PER_WORD);
			if (bit == -1)
			{
				out.writeBits(encodings[PSEUDO_EOF].length(), Integer.parseInt(encodings[PSEUDO_EOF], 2));
				return;
			}

			out.writeBits(encodings[bit].length(), Integer.parseInt(encodings[bit], 2));
		}
	}









	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out)
	{

		int val = in.readBits(BITS_PER_INT);
		if (val != HUFF_TREE)
			throw new HuffException("illegal header starts with " + val);

		HuffNode tree = getTree(in);
		

		readCompressedBits(tree, in, out);

		out.close();

	}

	/**
	 * Helper method for extracting the encoding at the beginning of the
	 * compressed file.
	 * 
	 * @param in buffered bit stream of the file from which tree is read.
	 * @return the root node of the tree
	 */
	private HuffNode getTree(BitInputStream in)
	{
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("failed to read bits");
		if (bit == 0)
		{
			HuffNode left = getTree(in);
			HuffNode right = getTree(in);
			return new HuffNode(0,0,left,right);
		}
		else 
		{
			int value = in.readBits(BITS_PER_WORD+1);
			System.out.println(value);
			return new HuffNode(value,0,null,null);
		}

	}

	/**
	 * helper method for reading from the compressed file and writing
	 * to output after extracting the encoding tree
	 * @param root Root node of the encoding tree to be used
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out)
	{
		HuffNode current = root; 
		while (true)
		{
			int bits = in.readBits(1);
			if (bits == -1)
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else 
			{ 
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;

				if (current.myLeft == null && current.myRight == null)
				{
					if (current.myValue == PSEUDO_EOF) 
						break;   // out of loop
					else
					{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
	}
}