package com.dolfin.space.ships;
import java.util.List;
/** Model coordinates in sixteenths of a block; shared collision, theme and render specification. */
public record ShipDesign(float width,float height,int theme,List<Part> parts) {
 public record Part(String name,float x,float y,float z,float w,float h,float d) {}
 private static Part p(String name,float x,float y,float z,float w,float h,float d){return new Part(name,x,y,z,w,h,d);}
 public static ShipDesign of(ShipTier tier){return switch(tier){
  case BASICA -> new ShipDesign(2.5f,3.75f,0xff49caca,List.of(
   p("hull",-10,8,-10,20,30,20),p("window",-7,27,-11,14,9,2),p("nose",-7,38,-7,14,13,14),p("tip",-3,51,-3,6,9,6),
   p("fin_left",-20,0,-2,10,18,4),p("fin_right",10,0,-2,10,18,4),p("fin_back",-2,0,10,4,18,10),p("engine",-7,0,-7,14,8,14)));
  case CARGA -> new ShipDesign(4.5f,3f,0xffefa545,List.of(
   p("hull",-18,8,-14,36,24,28),p("window",-13,22,-15,26,8,2),p("nose",-12,32,-10,24,16,20),
   p("pod_left",-31,8,-10,13,20,20),p("pod_right",18,8,-10,13,20,20),p("fin_left",-36,0,-3,9,15,6),p("fin_right",27,0,-3,9,15,6),
   p("engine_left",-25,0,-7,14,8,14),p("engine_right",11,0,-7,14,8,14)));
  case RAPIDA -> new ShipDesign(2.5f,4.5f,0xffba8bff,List.of(
   p("hull",-7,12,-7,14,36,14),p("window",-5,32,-8,10,12,2),p("nose",-5,48,-5,10,16,10),p("tip",-2,64,-2,4,8,4),
   p("wing_left",-20,2,-2,13,24,4),p("wing_right",7,2,-2,13,24,4),p("engine",-6,0,-6,12,12,12),p("stabilizer",-2,5,7,4,15,10)));
 };}
 public static boolean boarding(boolean sight,boolean clearSeat,double distanceSquared){return sight&&clearSeat&&distanceSquared<=36;}
}
