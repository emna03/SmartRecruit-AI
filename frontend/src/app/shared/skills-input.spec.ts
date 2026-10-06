import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SkillsInput } from './skills-input';

describe('SkillsInput', () => {
  function create() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(SkillsInput);
    const onChange = vi.fn();
    fixture.componentInstance.registerOnChange(onChange);
    return { component: fixture.componentInstance, fixture, onChange };
  }

  it('adds normalized skills and ignores case-insensitive duplicates', () => {
    const { component, onChange } = create();
    component.add('  Spring   Boot ');
    component.add('java');
    component.add('JAVA');
    component.add('   ');

    expect(component.skills()).toEqual(['Spring Boot', 'java']);
    expect(onChange).toHaveBeenLastCalledWith(['Spring Boot', 'java']);
  });

  it('splits typed text on commas', () => {
    const { component } = create();
    component.onInput('Java, Angular, Doc');

    expect(component.skills()).toEqual(['Java', 'Angular']);
    expect(component.query()).toBe(' Doc');
  });

  it('removes a skill and respects the maximum', () => {
    const { component, fixture } = create();
    fixture.componentRef.setInput('max', 2);
    component.writeValue(['Java', 'SQL']);
    component.add('Docker');
    expect(component.skills()).toEqual(['Java', 'SQL']);

    component.remove('Java');
    expect(component.skills()).toEqual(['SQL']);
  });

  it('renders the skills as badges', async () => {
    const { component, fixture } = create();
    component.writeValue(['Java', 'Angular']);
    await fixture.whenStable();

    const badges = (fixture.nativeElement as HTMLElement).querySelectorAll('.skill-badge');
    expect(badges.length).toBe(2);
    expect(badges[0].textContent).toContain('Java');
  });
});
