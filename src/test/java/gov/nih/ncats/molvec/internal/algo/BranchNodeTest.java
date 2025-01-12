package gov.nih.ncats.molvec.internal.algo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Test;

import gov.nih.ncats.molvec.internal.algo.BranchNode.TokenTree;
public class BranchNodeTest {
	
	@Test
	public void commonAtomsShouldGetCorrectReading(){
		testReadingSingle("C","C");
		testReadingSingle("O","O");
		testReadingSingle("H","H");
		testReadingSingle("N","N");
		testReadingSingle("Br","Br");
		testReadingSingle("F","F");
		testReadingSingle("S","S");
		testReadingSingle("P","P");
		testReadingSingle("Cl","Cl");
	}
	
	@Test
	public void carboxylicAcidShouldHave3NodesWithCorrectBonds(){
		assertEquals("-C(=O,-O)", BranchNode.interpretOCRStringAsAtom2("CO2H").toString());
	}
	@Test
	public void terminagedEsterShouldHave3NodesWithCorrectBonds(){
		assertEquals("-C(=O,-O)",BranchNode.interpretOCRStringAsAtom2("CO2").toString());
	}
	@Test
	public void parentheticalGroupShouldReturnPsuedoNode(){
		assertEquals("-?(-C(-C),-C(-C))",BranchNode.interpretOCRStringAsAtom2("(CH2CH3)2").toString());
	}
	@Test
	public void parentheticalGroupWithLeadAtomShouldReturnRealNode(){
		assertEquals("-N(-C(-C),-C(-C))",BranchNode.interpretOCRStringAsAtom2("N(CH2CH3)2").toString());
	}
	@Test
	public void methylEsterShouldHave4NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("CO2C").toString();
		
		assertEquals("-C(=O,-O(-C))",s);
	}
	
	@Test
	public void methyoxyEsterShouldHave5NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("CO2CH2OH").toString();
		assertEquals("-C(=O,-O(-C(-O)))",s);
	}
	
	@Test
	public void parseTree(){
		TokenTree tt=BranchNode.parseTokenTree("cH31OBr()");
		tt.getAllTrees(ll->{
			String full=ll.stream()
			  .map(tt1->tt1.getDisplay())
			  .collect(Collectors.joining("-"));
			System.out.println(full);
		});
	}
	
	@Test
	public void testParse(){
		Optional<Tuple<BranchNode,String>> op = BranchNode.parseBranchNode("(CH2O)3");
		Tuple<BranchNode,String> gg=op.get();
		System.out.println("Is linkable:" + gg.k().isLinkable());
		System.out.println(gg.k().toString() + " as " + gg.v());
	}
	
	@Test
	public void floridatedMethaneShouldHave4NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("CF3").toString();
		assertEquals("-C(-F,-F,-F)",s);
	}
	
	
	@Test
	public void chloridatedMethaneShouldHave4NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("CCl3").toString();
		assertEquals("-C(-Cl,-Cl,-Cl)",s);
	}
	
	@Test
	public void floridatedInverseMethaneShouldHave4NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("F3C").toString();
		assertEquals("-C(-F,-F,-F)",s);
	}
	
	@Test
	public void floridatedmethylEsterShouldHave4NodesWithCorrectBonds(){
		String s=BranchNode.interpretOCRStringAsAtom2("CO2CF3").toString();
		assertEquals("-C(=O,-O(-C(-F,-F,-F)))",s);
	}
	
	@Test
	public void shouldLookNice(){
		BranchNode s=BranchNode.interpretOCRStringAsAtom2("CCF3");
		s.generateCoordinates();
		List<String> coordStrings = new ArrayList<>();
		
		s.forEachBranchNode((a,b)->{
			Point2D p=b.getSuggestedPoint();
			coordStrings.add(b.getSymbol() + " :"+ p.getX() + "," + p.getY());
		});
		assertEquals("C :0.0,0.0",coordStrings.get(0));
		assertEquals("C :0.5000000000000001,-0.8660254037844386",coordStrings.get(1));
		assertEquals("F :3.3306690738754696E-16,-1.7320508075688774",coordStrings.get(2));
		assertEquals("F :1.5,-0.8660254037844386",coordStrings.get(3));
		assertEquals("F :1.0000000000000002,-1.7320508075688772",coordStrings.get(4));
	}
	
	@Test
	public void shouldAllowCarbonChainsToConnectOnBothSides(){
		String s="CH2CH2CH";
		BranchNode bn=BranchNode.interpretOCRStringAsAtom2(s);
		assertTrue(bn.canBeChain());		
	}
	
	public void testReadingSingle(String input, String expected){
		BranchNode bn= BranchNode.interpretOCRStringAsAtom2(input);
		assertTrue("Single input '" + input + "' should be a real node",bn.isRealNode());
		assertEquals(expected,bn.getSymbol());
	}

}
