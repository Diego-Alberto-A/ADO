import SwiftUI
import UIKit

struct ScreenTransitions: UIViewControllerRepresentable {
    @ObservedObject var session: Session
    func makeUIViewController(context: Context) -> TransitionController { TransitionController(session:session) }
    func updateUIViewController(_ controller: TransitionController, context: Context) { }
}

@MainActor final class TransitionController: UIViewController {
    private let session: Session
    private let host: UIHostingController<ScreenContent>
    init(session: Session) {
        self.session=session; host=UIHostingController(rootView:ScreenContent(session:session))
        super.init(nibName:nil,bundle:nil)
    }
    required init?(coder: NSCoder) { fatalError("Programmatic controller") }
    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(host); view.addSubview(host.view); host.didMove(toParent:self)
        host.view.backgroundColor = .clear
        session.transition = { [weak self] motion, action in self?.animate(motion,action:action) }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); host.view.frame=view.bounds }
    private func animate(_ motion: ScreenMotion, action: @escaping () -> Void) {
        guard !session.transitioning, view.bounds.width > 0 else { return }
        session.transitioning=true
        let image=UIGraphicsImageRenderer(bounds:host.view.bounds).image { _ in host.view.drawHierarchy(in:host.view.bounds,afterScreenUpdates:false) }
        let previous=UIImageView(image:image); previous.frame=view.bounds; previous.isAccessibilityElement=false
        view.addSubview(previous); host.view.accessibilityElementsHidden=true; view.isUserInteractionEnabled=false
        action()
        // A fresh root ensures the new SwiftUI screen is laid out before animation begins.
        host.rootView=ScreenContent(session:session)
        DispatchQueue.main.async { [self] in
            host.view.setNeedsLayout(); host.view.layoutIfNeeded()
            let duration=motion.sliding ? 0.42 : 0.7
            let finish: () -> Void = { [self] in
                previous.removeFromSuperview(); host.view.layer.mask=nil; host.view.transform = .identity
                host.view.accessibilityElementsHidden=false; view.isUserInteractionEnabled=true; session.transitioning=false
            }
            if motion.sliding {
                view.clipsToBounds=true
                let direction: CGFloat=motion.backwards ? -1 : 1, width=view.bounds.width
                host.view.transform=CGAffineTransform(translationX:direction*width,y:0)
                UIView.animate(withDuration:duration,delay:0,options:[.curveEaseInOut]) {
                    self.host.view.transform = .identity
                    previous.transform=CGAffineTransform(translationX:-direction*width,y:0)
                } completion: { _ in finish() }
            } else {
                let top=motion.backwards ? previous : host.view!
                if !motion.backwards { view.bringSubviewToFront(host.view) }
                let center=CGPoint(x:view.bounds.midX,y:view.bounds.midY)
                let radius=hypot(view.bounds.width,view.bounds.height)*0.5
                func circle(_ r: CGFloat) -> CGPath { UIBezierPath(ovalIn:CGRect(x:center.x-r,y:center.y-r,width:r*2,height:r*2)).cgPath }
                let mask=CAShapeLayer(), rim=CAShapeLayer()
                let from=circle(motion.backwards ? radius : 0.001), to=circle(motion.backwards ? 0.001 : radius)
                mask.path=to; top.layer.mask=mask
                rim.path=circle((motion.backwards ? 0.001 : radius)+4.5)
                rim.fillColor=UIColor.clear.cgColor; rim.strokeColor=UIColor(red:245/255,green:230/255,blue:197/255,alpha:1).cgColor; rim.lineWidth=9
                view.layer.addSublayer(rim)
                let animation=CABasicAnimation(keyPath:"path"); animation.fromValue=from; animation.toValue=to; animation.duration=duration
                animation.timingFunction=CAMediaTimingFunction(controlPoints:0.4,0,0.2,1)
                mask.add(animation,forKey:"iris")
                let rimAnimation=animation.copy() as! CABasicAnimation
                rimAnimation.fromValue=circle((motion.backwards ? radius : 0.001)+4.5)
                rimAnimation.toValue=rim.path
                rim.add(rimAnimation,forKey:"iris")
                DispatchQueue.main.asyncAfter(deadline:.now()+duration) { rim.removeFromSuperlayer(); finish() }
            }
        }
    }
}
