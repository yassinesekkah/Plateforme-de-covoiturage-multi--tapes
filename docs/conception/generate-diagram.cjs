const fs = require('fs');
const path = require('path');
const out = __dirname;
const classes = [
 ['Conducteur',60,90,['id : Long','nom : String','prenom : String','email : String {unique}','telephone : String','numeroPermis : String {unique}']],
 ['Vehicule',490,90,['id : Long','marque : String','modele : String','immatriculation : String {unique}','couleur : String','capacite : int {1..8}']],
 ['Passager',1050,90,['id : Long','nom : String','prenom : String','email : String {unique}','telephone : String']],
 ['Trajet',60,500,['id : Long','villeDepart : String','villeArrivee : String','dateDepart : LocalDateTime','placesProposees : int','statut : StatutTrajet','/placesRestantes : int {calcule}']],
 ['PointArret',490,500,['id : Long','ville : String','ordre : int','heurePassage : LocalDateTime','prixDepuisDepart : BigDecimal']],
 ['Reservation',1050,500,['id : Long','nombrePlaces : int','montantTotal : BigDecimal','dateReservation : LocalDateTime','statut : StatutReservation']]
];
// Each relation lists source, target, role, multiplicities and routed points.
const edges = [
 ['Conducteur','Vehicule','possede','1','0..*',[[380,180],[490,180]]],
 ['Conducteur','Trajet','publie','1','0..*',[[150,360],[150,500]]],
 ['Vehicule','Trajet','utilise par','1','0..*',[[570,360],[570,415],[300,415],[300,500]]],
 ['Trajet','PointArret','contient','1','2..*',[[380,610],[490,610]],true],
 ['Passager','Reservation','effectue','1','0..*',[[1210,360],[1210,500]]],
 ['Trajet','Reservation','recoit','1','0..*',[[220,770],[220,885],[1210,885],[1210,770]]],
 ['PointArret','Reservation','montee','1','0..*',[[810,580],[1050,580]]],
 ['PointArret','Reservation','descente','1','0..*',[[810,690],[1050,690]]]
];
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const text=(x,y,s,size=16,anchor='start')=>`<text x="${x}" y="${y}" font-size="${size}" text-anchor="${anchor}">${esc(s)}</text>`;
let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1450" height="1100" viewBox="0 0 1450 1100"><rect width="1450" height="1100" fill="white"/><g font-family="Arial, sans-serif" fill="#18324a">${text(60,42,'COV-6 — Diagramme de classes : covoiturage',26)}`;
let cells='<mxCell id="0"/><mxCell id="1" parent="0"/>';
for(const [name,x,y,attrs] of classes){
 svg+=`<rect x="${x}" y="${y}" width="320" height="270" rx="4" fill="#f6f9fc" stroke="#355873" stroke-width="2"/><path d="M ${x} ${y+44} h 320" stroke="#355873"/>${text(x+160,y+29,name,20,'middle')}`;
 attrs.forEach((a,i)=>svg+=text(x+15,y+73+i*25,a,16));
 const val=`<b>${name}</b><hr>${attrs.map(esc).join('<br>')}`;
 cells+=`<mxCell id="${name}" value="${esc(val)}" style="rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=14;fontSize=16;fillColor=#f6f9fc;strokeColor=#355873;" vertex="1" parent="1"><mxGeometry x="${x}" y="${y}" width="320" height="270" as="geometry"/></mxCell>`;
}
edges.forEach(([src,dst,role,a,b,pts,composition],i)=>{
 const [p,q]=[pts[0],pts.at(-1)];
 svg+=`<polyline points="${pts.map(p=>p.join(',')).join(' ')}" fill="none" stroke="#355873" stroke-width="2"/>`;
 if(composition)svg+=`<polygon points="380,610 390,604 400,610 390,616" fill="#355873"/>`;
 const horizontal=p[1]===q[1];
 if(horizontal){svg+=text(p[0]+23,p[1]-12,a,15)+text(q[0]-23,q[1]-12,b,15,'end')+text((p[0]+q[0])/2,p[1]+24,role,15,'middle');}
 else if(role==='recoit'){svg+=text(p[0]+12,p[1]+25,a,15)+text(q[0]+12,q[1]+25,b,15)+text(715,875,role,16,'middle');}
 else {svg+=text(p[0]+12,p[1]+24,a,15)+text(q[0]+12,q[1]-12,b,15)+text(pts.length>2?425:p[0]+12,pts.length>2?405:440,role,15);}
 cells+=`<mxCell id="r${i}" value="${role}" edge="1" parent="1" source="${src}" target="${dst}" style="edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=none;startArrow=${composition?'diamond':'none'};startFill=1;fontSize=15;strokeColor=#355873;exitX=${(p[0]-classes.find(c=>c[0]===src)[1])/320};exitY=${(p[1]-classes.find(c=>c[0]===src)[2])/270};entryX=${(q[0]-classes.find(c=>c[0]===dst)[1])/320};entryY=${(q[1]-classes.find(c=>c[0]===dst)[2])/270};"><mxGeometry relative="1" as="geometry"><Array as="points">${pts.slice(1,-1).map(([x,y])=>`<mxPoint x="${x}" y="${y}"/>`).join('')}</Array></mxGeometry></mxCell>`;
 for(const [j,label,relative] of [[0,a,-0.85],[1,b,0.85]])cells+=`<mxCell id="r${i}m${j}" value="${label}" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;fontSize=15;" vertex="1" connectable="0" parent="r${i}"><mxGeometry x="${relative}" y="-16" relative="1" as="geometry"><mxPoint as="offset"/></mxGeometry></mxCell>`;
});
const notes=['StatutTrajet : PUBLIE | COMPLET | TERMINE | ANNULE','StatutReservation : CONFIRMEE | ANNULEE','1 = exactement un ; 0..* = zero ou plusieurs ; 2..* = au moins deux. Losange plein = composition.', 'Un segment = deux points du meme trajet, montee avant descente. Aucune classe Segment.', 'Places restantes calculees sur tout le trajet. Depart/arrivee/date coherents avec les premier/dernier points.'];
notes.forEach((s,i)=>svg+=text(60,940+i*28,s,16));
svg+='</g></svg>';
cells+=`<mxCell id="notes" value="${esc(notes.join('\n'))}" style="text;whiteSpace=wrap;html=0;align=left;verticalAlign=top;fontSize=16;" vertex="1" parent="1"><mxGeometry x="60" y="930" width="1300" height="160" as="geometry"/></mxCell>`;
fs.writeFileSync(path.join(out,'diagramme-classes.svg'),svg);
fs.writeFileSync(path.join(out,'diagramme-classes.drawio'),`<mxfile host="app.diagrams.net"><diagram name="COV-6 - Classes"><mxGraphModel dx="1450" dy="1100" grid="1" gridSize="10" page="1" pageWidth="1450" pageHeight="1100"><root>${cells}</root></mxGraphModel></diagram></mxfile>`);
let puml='@startuml\nskinparam classAttributeIconSize 0\nhide methods\n';
for(const [name,,,attrs] of classes)puml+=`class ${name} {\n${attrs.map(a=>'  '+a).join('\n')}\n}\n`;
for(const [src,dst,role,a,b,,composition] of edges)puml+=`${src} "${a}" ${composition?'*--':'--'} "${b}" ${dst} : ${role}\n`;
puml+='enum StatutTrajet {\n PUBLIE\n COMPLET\n TERMINE\n ANNULE\n}\nenum StatutReservation {\n CONFIRMEE\n ANNULEE\n}\n@enduml\n';
fs.writeFileSync(path.join(out,'diagramme-classes.puml'),puml);
console.log('Generated diagramme-classes.drawio, .svg and .puml');
